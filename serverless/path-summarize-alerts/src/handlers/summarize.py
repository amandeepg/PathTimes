import asyncio
import json
import os
from typing import Optional

import boto3
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.parser import parse
from aws_lambda_powertools.utilities.typing import LambdaContext
from opentelemetry import trace
from pydantic import BaseModel

from ..lib.constants import LlamaThreeThree70b
from ..lib.models import CacheResponse
from ..lib.summarizer import AlertSummarizer, RateLimitedException

logger = Logger()
tracer = trace.get_tracer(__name__)


class SummarizeEventQueryParams(BaseModel):
    input: str
    skip_cache: Optional[str] = None


class SummarizeEvent(BaseModel):
    queryStringParameters: SummarizeEventQueryParams


class SummarizerLambda:
    def __init__(self):
        self._summarizer = AlertSummarizer()
        self._lambda_client = boto3.client("lambda")

    @tracer.start_as_current_span("create_summarize_response")
    def _create_response(self, input_text: str, skip_cache: bool) -> dict:
        loop = asyncio.get_event_loop()
        result = loop.run_until_complete(
            self._summarize_and_schedule(input_text, skip_cache=skip_cache)
        )
        logger.info("Successfully processed request")
        logger.debug(f"Response: {result.model_dump_json()}")

        return {
            "statusCode": 200,
            "body": result.model_dump_json(),
            "headers": {
                "Content-Type": "application/json",
                "Cache-Control": "max-age=86400",  # Cache for 24 hours
            },
        }

    @tracer.start_as_current_span("summarize_and_schedule")
    async def _summarize_and_schedule(
        self, input_text: str, skip_cache: bool
    ) -> CacheResponse:
        result = await self._summarizer.summarize(
            input_text=input_text,
            skip_cache=skip_cache,
            model=LlamaThreeThree70b(),
        )

        if not result.cached:
            self._lambda_client.invoke(
                FunctionName=os.environ["MULTISUMMARIZER_LAMBDA_NAME"],
                InvocationType="Event",
                Payload=json.dumps({"original_text_key": result.hash_key}),
            )

        return result

    def summarize(self, event: dict) -> dict:
        logger.info("Received new request")
        logger.debug(f"Event: {json.dumps(event)}")

        try:
            parsed_event: SummarizeEvent = parse(model=SummarizeEvent, event=event)
            skip_cache_magic_word = os.environ.get("SKIP_CACHE_MAGIC_WORD")

            return self._create_response(
                input_text=parsed_event.queryStringParameters.input,
                skip_cache=(
                    parsed_event.queryStringParameters.skip_cache
                    == skip_cache_magic_word
                ),
            )
        except RateLimitedException as e:
            logger.exception(f"Rate limit exceeded. {e}")
            return {
                "statusCode": 429,
                "body": json.dumps({"error": "Rate limit exceeded"}),
                "headers": {"Content-Type": "application/json"},
            }
        except Exception as e:
            logger.exception("Error processing request")
            return {
                "statusCode": 500,
                "body": json.dumps({"error": str(e)}),
                "headers": {"Content-Type": "application/json"},
            }


handler = SummarizerLambda()


@logger.inject_lambda_context
def handle(event: dict, context: LambdaContext) -> dict:
    return handler.summarize(event)
