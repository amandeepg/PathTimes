import asyncio
import json
import time

import boto3
from aws_lambda_powertools import Logger
from baml_py import ClientRegistry
from botocore.exceptions import ClientError
from opentelemetry import trace

from baml_client import b
from baml_client.types import (
    AlertSummary,
    AffectedStations,
    AffectedRoutes,
    IsRelevant,
    IsDelay,
)
from .cache import CacheService
from .constants import BUCKET_NAME, BUCKET_NAME_RATE_LIMIT, LlmClient
from .models import CacheResponse, AlertSummaryContainer

logger = Logger()
tracer = trace.get_tracer(__name__)


class RateLimitedException(Exception):
    pass


class AlertSummarizer:
    def __init__(self):
        self.s3_client = boto3.client("s3")
        self.cache_service = CacheService(BUCKET_NAME)
        logger.info("Initialized AlertSummarizer")

    @tracer.start_as_current_span("summarizer.summarize")
    async def summarize(
        self, input_text: str, model: LlmClient, skip_cache: bool = False
    ) -> CacheResponse:
        """Summarize the input text using OpenAI API with caching."""
        logger.info(
            f"Processing new summarization request. Input length: {len(input_text)}"
        )
        logger.debug(f"Raw input text: {input_text}")

        hash_key = self.cache_service.hash_key(input_text, model)

        # Check cache
        cached_response = self.cache_service.get(hash_key) if not skip_cache else None
        if cached_response:
            logger.info(f"Cache hit for hash: {hash_key}")
            response_data = CacheResponse.model_validate_json(cached_response)
            response_data.cached = True
            return response_data

        if self.should_be_rate_limited(hash_key):
            logger.info("Rate limited")
            raise RateLimitedException("Rate limited")

        try:
            response_data = CacheResponse(
                input=input_text,
                model=model.id(),
                cache_version=CacheService.hash_category_key(),
                response=await self.get_ai_response(input_text, model),
                cached=False,
                hash_key=hash_key,
            )

            # Save to cache
            self.cache_service.save(hash_key, response_data.model_dump_json())

            return response_data

        except Exception as e:
            logger.error(f"Error calling OpenAI API: {str(e)}", exc_info=True)
            logger.error(f"Input text length: {len(input_text)}")
            raise

    @tracer.start_as_current_span("summarizer.should_be_rate_limited")
    def should_be_rate_limited(self, hash_key: str):
        try:
            # Get the object from S3
            response = self.s3_client.get_object(
                Bucket=BUCKET_NAME_RATE_LIMIT, Key=hash_key
            )
            # read json from the s3 response body
            data = json.loads(response["Body"].read().decode("utf-8"))
            # Check if file is older than 30 seconds
            should_be_rate_limited = time.time() - float(data["LastModified"]) <= 30.0
        except ClientError as e:
            if e.response["Error"]["Code"] == "404":
                should_be_rate_limited = False
            else:
                logger.exception(f"Error checking if rate limited {e}")
                should_be_rate_limited = False
        except Exception as e:
            logger.exception(f"Error checking if rate limited {e}")
            should_be_rate_limited = False
        if not should_be_rate_limited:
            self.s3_client.put_object(
                Bucket=BUCKET_NAME_RATE_LIMIT,
                Key=hash_key,
                Body=json.dumps({"LastModified": str(time.time())}),
                ContentType="application/json",
            )
        return should_be_rate_limited

    # [ERROR]
    # 2025 - 02 - 25
    # T05: 01:10.658
    # Z
    # 9193869
    # d - a9f8 - 45
    # c1 - 9
    # a0d - d6e40b80bc41
    # Error
    # checking if rate
    # limited
    # An
    # error
    # occurred(NoSuchKey)
    # when
    # calling
    # the
    # GetObject
    # operation: The
    # specified
    # key
    # does
    # not exist.

    @tracer.start_as_current_span("summarizer.get_ai_response")
    async def get_ai_response(
        self, input_text: str, model: LlmClient
    ) -> AlertSummaryContainer:
        trace.get_current_span().set_attribute(key="llm", value=model.id())
        cr = self.client_registry(model)

        summary, is_delay, is_relevant, affected_area = await asyncio.gather(
            self.get_alert_summary(cr, input_text),
            self.is_delay_alert(cr, input_text),
            self.is_relevant_alert(cr, input_text),
            self.get_affected_area(cr, input_text),
        )

        return AlertSummaryContainer(
            text=summary.alert_summary,
            is_delay=is_delay.is_delay,
            is_relevant=is_relevant.is_relevant,
            affected_area=affected_area,
        )

    @tracer.start_as_current_span("summarizer.get_affected_area")
    async def get_affected_area(
        self, cr: object, input_text: str
    ) -> AffectedStations | AffectedRoutes | None:
        return await b.GetAffectedArea(input_text, {"client_registry": cr})

    @tracer.start_as_current_span("summarizer.is_relevant_alert")
    async def is_relevant_alert(self, cr: object, input_text: str) -> IsRelevant:
        return await b.IsRelevantAlert(input_text, {"client_registry": cr})

    @tracer.start_as_current_span("summarizer.is_delay_alert")
    async def is_delay_alert(self, cr: object, input_text: str) -> IsDelay:
        return await b.IsDelayAlert(input_text, {"client_registry": cr})

    @tracer.start_as_current_span("summarizer.get_alert_summary")
    async def get_alert_summary(self, cr: object, input_text: str) -> AlertSummary:
        return await b.GetAlertSummary(input_text, {"client_registry": cr})

    @staticmethod
    def client_registry(model: LlmClient) -> ClientRegistry:
        cr = ClientRegistry()
        model.add_to_registry(cr)
        cr.set_primary(model.id())
        return cr
