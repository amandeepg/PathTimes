import asyncio
import json
from typing import Any, Dict

from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.parser import parse
from aws_lambda_powertools.utilities.typing import LambdaContext
from opentelemetry import trace
from pydantic import BaseModel

from lib.cache import CacheService
from lib.llm_client_base import LlmClient
from lib.llm_clients import ALL_LLM_CLIENTS
from lib.summarizer import AlertSummarizer

logger = Logger()
tracer = trace.get_tracer(__name__)


class MultiSummarizeEvent(BaseModel):
    original_text_key: str


class MultiSummarizer:
    def __init__(self) -> None:
        self._summarizer = AlertSummarizer()
        self._cache_service = CacheService()

    @tracer.start_as_current_span("multisumm.create_multisummarize_response")
    async def _create_multisummarize_response(
        self, original_text_key: str
    ) -> Dict[str, Any]:
        cached_response = self._cache_service.get(original_text_key)
        if not cached_response:
            return {
                "statusCode": 404,
                "body": json.dumps({"error": "Original text not found"}),
                "headers": {"Content-Type": "application/json"},
            }

        original_input_text = cached_response.input

        await self._async_multisummarize(original_input_text)
        logger.info("Successfully processed request")

        return {
            "statusCode": 200,
            "headers": {"Content-Type": "application/json"},
        }

    @tracer.start_as_current_span("multisumm.async_multisummarize")
    async def _async_multisummarize(self, input_text: str) -> None:
        async def summarize_with_exception_handling(client: LlmClient):
            try:
                # skip if the model is too expensive and alert is for an elevator
                if client.cost() >= 1.0 and "elevator" in input_text:
                    return None

                _, _, token = await self._summarizer.check_for_cached(
                    input_text=input_text, model=client
                )
                summary = await self._summarizer.summarize(token)
                return summary
            except Exception as e:
                logger.error(f"Exception occurred while summarizing with {client}: {e}")
                return None

        tasks = [
            summarize_with_exception_handling(client) for client in ALL_LLM_CLIENTS
        ]
        await asyncio.gather(*tasks)

    def multisummarize(self, event: Dict[str, Any]) -> Dict[str, Any]:
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
def handle(event: Dict[str, Any], context: LambdaContext) -> Dict[str, Any]:
    return handler.multisummarize(event)
