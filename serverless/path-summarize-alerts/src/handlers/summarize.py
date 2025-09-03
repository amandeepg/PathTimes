import asyncio
import json
import os
from typing import Any, Dict, Optional

import boto3
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.parser import parse
from aws_lambda_powertools.utilities.typing import LambdaContext
from opentelemetry import trace
from pydantic import BaseModel

from lib.llm_clients import FAST_LLM, PREFERRED_LLM, GptOss120
from lib.models import AlertSummaryAiResponse
from lib.summarizer import AlertSummarizer, CachingPolicy

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
        self._lambda_client = boto3.client("lambda")  # pyright: ignore[reportUnknownMemberType]

    class SummarizeResult(BaseModel):
        summary: AlertSummaryAiResponse
        is_preferred: bool

    @tracer.start_as_current_span("create_summarize_response")
    def _create_response(self, input_text: str, skip_cache: bool) -> Dict[str, Any]:
        result = asyncio.run(
            self._summarize_and_schedule(input_text, skip_cache=skip_cache)
        )
        result_json = result.summary.model_dump_json()
        is_preferred = result.is_preferred

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
    ) -> SummarizeResult:
        preferred_llm = GptOss120() if "elevator" in input_text else PREFERRED_LLM
        pref_result = await self._summarizer.summarize(
            input_text=input_text,
            model=preferred_llm,
            caching_policy=CachingPolicy.CACHE_ONLY,
        )
        if pref_result:
            logger.info("Returning preferred result from cache")
            return SummarizerLambda.SummarizeResult(
                summary=pref_result,
                is_preferred=True,
            )

        asyncio.get_event_loop().set_task_factory(asyncio.eager_task_factory)
        summarize_task = asyncio.create_task(
            self._summarizer.summarize(
                input_text=input_text,
                model=FAST_LLM,
                caching_policy=CachingPolicy.NO_CACHE
                if skip_cache
                else CachingPolicy.USE_CACHING,
            )
        )
        summarized_alert = await summarize_task

        if summarized_alert:
            self._lambda_client.invoke(
                FunctionName=os.environ["MULTISUMMARIZER_LAMBDA_NAME"],
                InvocationType="Event",
                Payload=json.dumps(
                    {"original_text_key": summarized_alert.input_string_hash}
                ),
            )

        if summarized_alert is None:
            raise ValueError("Failed to generate summary with fast LLM")
        return SummarizerLambda.SummarizeResult(
            summary=summarized_alert,
            is_preferred=False,
        )

    def summarize(self, event: Dict[str, Any]) -> Dict[str, Any]:
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
        except Exception as e:
            logger.exception("Error processing request")
            return {
                "statusCode": 500,
                "body": json.dumps({"error": str(e)}),
                "headers": {"Content-Type": "application/json"},
            }


handler = SummarizerLambda()


@logger.inject_lambda_context
def handle(event: Dict[str, Any], context: LambdaContext) -> Dict[str, Any]:
    return handler.summarize(event)
