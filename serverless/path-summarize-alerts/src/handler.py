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

from .lib.cache import CacheService
from .lib.constants import (
    BUCKET_NAME,
    ALL_LLM_CLIENTS,
    LlamaThreeThree70b,
)
from .lib.models import CacheResponse
from .lib.summarizer import AlertSummarizer, RateLimitedException

logger = Logger()
tracer = trace.get_tracer(__name__)
summarizer = AlertSummarizer()


class SummarizeEventQueryParams(BaseModel):
    input: str
    skip_cache: Optional[str] = None


class SummarizeEvent(BaseModel):
    queryStringParameters: SummarizeEventQueryParams


@logger.inject_lambda_context
def summarize(event: dict, context: LambdaContext):
    logger.info("Received new request")
    logger.debug(f"Event: {json.dumps(event)}")

    try:
        parsed_event: SummarizeEvent = parse(model=SummarizeEvent, event=event)
        skip_cache_magic_word = os.environ.get("SKIP_CACHE_MAGIC_WORD")
        return create_summarize_response(
            input_text=parsed_event.queryStringParameters.input,
            skip_cache=(
                parsed_event.queryStringParameters.skip_cache == skip_cache_magic_word
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


@tracer.start_as_current_span("create_summarize_response")
def create_summarize_response(input_text: str, skip_cache: bool) -> dict:
    loop = asyncio.get_event_loop()
    result = loop.run_until_complete(
        summarize_and_schedule(input_text, skip_cache=skip_cache)
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
async def summarize_and_schedule(input_text: str, skip_cache: bool) -> CacheResponse:
    result = await summarizer.summarize(
        input_text=input_text,
        skip_cache=skip_cache,
        model=LlamaThreeThree70b(),
    )
    if not result.cached:
        boto3.client("lambda").invoke(
            FunctionName=os.environ["MULTISUMMARIZER_LAMBDA_NAME"],
            InvocationType="Event",
            Payload=json.dumps({"original_text_key": result.hash_key}),
        )
    return result


class MultiSummarizeEvent(BaseModel):
    original_text_key: str


@logger.inject_lambda_context
def multisummarize(event: dict, context: LambdaContext):
    logger.info("Received new request")
    logger.debug(f"Event: {json.dumps(event)}")

    try:
        parsed_event: MultiSummarizeEvent = parse(
            model=MultiSummarizeEvent, event=event
        )
        return create_multisummarize_response(
            original_text_key=parsed_event.original_text_key
        )

    except RateLimitedException as e:
        logger.exception(f"Custom rate limit exceeded {e}")
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


@tracer.start_as_current_span("create_multisummarize_response")
def create_multisummarize_response(original_text_key: str) -> dict:
    original_input_text = CacheResponse.model_validate_json(
        CacheService(BUCKET_NAME).get(original_text_key)
    ).input
    loop = asyncio.get_event_loop()
    loop.run_until_complete(async_multisummarize(original_input_text))
    logger.info("Successfully processed request")
    return {
        "statusCode": 200,
        "headers": {
            "Content-Type": "application/json",
            "Cache-Control": "max-age=86400",  # Cache for 24 hours
        },
    }


@tracer.start_as_current_span("async_multisummarize")
async def async_multisummarize(input_text: str) -> None:
    tasks = [
        summarizer.summarize(input_text, model=client) for client in ALL_LLM_CLIENTS
    ]
    await asyncio.gather(*tasks)
