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

from ..lib.llm_clients import PREFERRED_LLM, FAST_LLM
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
        result = asyncio.run(
            self._summarize_and_schedule(input_text, skip_cache=skip_cache)
        )
        result_json = result[0].model_dump_json()
        is_preferred = result[1]

        logger.info("Successfully processed request")
        logger.debug(f"Response: {result_json}")
        logger.debug(f"Is preferred: {is_preferred}")

        return {
            "statusCode": 200,
            "body": result_json,
            "headers": {
                "Content-Type": "application/json",
                # if preferred then cached for 24 hours, otherwise 5 mins
                "Cache-Control": "max-age=86400" if is_preferred else "max-age=300",
            },
        }

    @tracer.start_as_current_span("summarize_and_schedule")
    async def _summarize_and_schedule(
        self, input_text: str, skip_cache: bool
    ) -> tuple[CacheResponse, bool]:
        pref_result = await self._summarizer.summarize_from_cache(
            input_text=input_text,
            model=PREFERRED_LLM,
        )
        if pref_result:
            logger.info("Returning preferred result from cache")
            return pref_result, True

        _, hash_key, token = await self._summarizer.check_for_cached(
            input_text=input_text,
            model=FAST_LLM,
            skip_cache=skip_cache,
        )

        asyncio.get_event_loop().set_task_factory(asyncio.eager_task_factory)
        summarize_task = asyncio.create_task(self._summarizer.summarize(token))

        self._lambda_client.invoke(
            FunctionName=os.environ["MULTISUMMARIZER_LAMBDA_NAME"],
            InvocationType="Event",
            Payload=json.dumps({"original_text_key": hash_key}),
        )

        return await summarize_task, False

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
