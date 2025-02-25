import asyncio
import json

from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.parser import parse
from aws_lambda_powertools.utilities.typing import LambdaContext
from opentelemetry import trace
from pydantic import BaseModel

from ..lib.cache import CacheService
from ..lib.constants import ALL_LLM_CLIENTS, BUCKET_NAME
from ..lib.models import CacheResponse
from ..lib.summarizer import AlertSummarizer

logger = Logger()
tracer = trace.get_tracer(__name__)


class MultiSummarizeEvent(BaseModel):
    original_text_key: str


class MultiSummarizer:
    def __init__(self) -> None:
        self.summarizer = AlertSummarizer()
        self.cache_service = CacheService(BUCKET_NAME)

    @tracer.start_as_current_span("multisumm.create_multisummarize_response")
    async def _create_multisummarize_response(self, original_text_key: str) -> dict:
        cached_response = self.cache_service.get(original_text_key)
        original_input_text = CacheResponse.model_validate_json(cached_response).input

        await self._async_multisummarize(original_input_text)
        logger.info("Successfully processed request")

        return {
            "statusCode": 200,
            "headers": {
                "Content-Type": "application/json",
                "Cache-Control": "max-age=86400",  # Cache for 24 hours
            },
        }

    @tracer.start_as_current_span("multisumm.async_multisummarize")
    async def _async_multisummarize(self, input_text: str) -> None:
        tasks = [
            self.summarizer.summarize(input_text, model=client)
            for client in ALL_LLM_CLIENTS
        ]
        await asyncio.gather(*tasks)

    def multisummarize(self, event: dict) -> dict:
        logger.info("Received new request")
        logger.debug("Event: %s", json.dumps(event))

        try:
            parsed_event = parse(model=MultiSummarizeEvent, event=event)
            return asyncio.run(
                self._create_multisummarize_response(
                    original_text_key=parsed_event.original_text_key
                )
            )
        except Exception as exc:
            logger.exception("Error processing request")
            return {
                "statusCode": 500,
                "body": json.dumps({"error": str(exc)}),
                "headers": {"Content-Type": "application/json"},
            }


handler = MultiSummarizer()


@logger.inject_lambda_context
def handle(event: dict, context: LambdaContext) -> dict:
    return handler.multisummarize(event)
