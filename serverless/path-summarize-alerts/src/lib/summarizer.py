import asyncio
import json
import os
import time

import boto3
from aws_lambda_powertools import Logger
from baml_py import ClientRegistry
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
from .constants import BUCKET_NAME, BUCKET_NAME_RATE_LIMIT, OpenRouterClient
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
        self, input_text: str, skip_cache: bool, model: OpenRouterClient
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
                model=model.value[0],
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

    @tracer.start_as_current_span("summarizer.get_ai_response")
    async def get_ai_response(
        self, input_text: str, model: OpenRouterClient
    ) -> AlertSummaryContainer:
        cr = self.client_registry()
        cr.set_primary(model.value[0])
        trace.get_current_span().set_attribute(key="llm", value=model.value[1])

        summary_task = self.get_alert_summary(cr, input_text)
        delay_task = self.is_delay_alert(cr, input_text)
        relevance_task = self.is_relevant_alert(cr, input_text)
        affected_area_task = self.get_affected_area(cr, input_text)

        summary, is_delay, is_relevant, affected_area = await asyncio.gather(
            summary_task, delay_task, relevance_task, affected_area_task
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
        # async with tracer.provider.in_subsegment_async("get_affected_area"):
        return await b.GetAffectedArea(input_text, {"client_registry": cr})

    @tracer.start_as_current_span("summarizer.is_relevant_alert")
    async def is_relevant_alert(self, cr: object, input_text: str) -> IsRelevant:
        #         async with tracer.provider.in_subsegment_async("is_relevant_alert"):
        return await b.IsRelevantAlert(input_text, {"client_registry": cr})

    @tracer.start_as_current_span("summarizer.is_delay_alert")
    async def is_delay_alert(self, cr: object, input_text: str) -> IsDelay:
        #         async with tracer.provider.in_subsegment_async("is_delay_alert"):
        return await b.IsDelayAlert(input_text, {"client_registry": cr})

    @tracer.start_as_current_span("summarizer.get_alert_summary")
    async def get_alert_summary(self, cr: object, input_text: str) -> AlertSummary:
        #         async with tracer.provider.in_subsegment_async("get_alert_summary"):
        return await b.GetAlertSummary(input_text, {"client_registry": cr})

    @staticmethod
    def client_registry() -> ClientRegistry:
        cr = ClientRegistry()
        for client in OpenRouterClient:
            name = client.value[0]
            model = client.value[1]
            cr.add_llm_client(
                name=name,
                provider="openai-generic",
                options={
                    "model": model,
                    "temperature": 0.4,
                    "api_key": os.environ.get("OPENROUTER_API_KEY"),
                    "base_url": "https://openrouter.ai/api/v1",
                    "headers": {
                        "HTTP-Referer": os.environ.get("OPENROUTER_APP_URL"),
                        "X-Title": os.environ.get("OPENROUTER_APP_NAME"),
                    },
                },
            )
        return cr
