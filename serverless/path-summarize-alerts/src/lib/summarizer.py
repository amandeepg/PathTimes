import asyncio
import hashlib
import json
import os
import time

from baml_client import b
from baml_client.types import AlertSummary, IsDelay, IsRelevant, AffectedStations, AffectedRoutes

import boto3
import instructor
import langsmith.wrappers
from aws_lambda_powertools import Logger, Tracer
from openai import OpenAI

from .cache import CacheService
from .constants import BUCKET_NAME, MODEL_NAME, BUCKET_NAME_RATE_LIMIT, SYSTEM_MESSAGE
from .models import CacheResponse, AlertSummaryContainer

logger = Logger()
tracer = Tracer()


class RateLimitedException(Exception):
    pass


class AlertSummarizer:
    def __init__(self):
        self.s3_client = boto3.client("s3")
        self.lambda_client = boto3.client('lambda')
        self.client = instructor.from_openai(
            langsmith.wrappers.wrap_openai(
                OpenAI(base_url="https://openrouter.ai/api/v1")
            ),
            mode=instructor.Mode.JSON,
        )
        self.cache_service = CacheService(BUCKET_NAME)
        logger.info("Initialized AlertSummarizer")

    @staticmethod
    def hash_string(input_string: str) -> str:
        """Create an SHA-1 hash of the input string."""
        hash_value = hashlib.sha1(input_string.encode("utf-8")).hexdigest()
        logger.debug(
            f"Generated hash: {hash_value} for input length: {len(input_string)}"
        )
        return hash_value

    @tracer.capture_method
    def summarize(self, input_text: str, skip_cache: bool) -> CacheResponse:
        """Summarize the input text using OpenAI API with caching."""
        logger.info(
            f"Processing new summarization request. Input length: {len(input_text)}"
        )
        logger.debug(f"Raw input text: {input_text}")

        hash_key = self.hash_string(input_text)

        # Check cache
        cached_response = self.cache_service.get(hash_key) if not skip_cache else None
        if cached_response:
            logger.info(f"Cache hit for hash: {hash_key}")
            response_data = CacheResponse.model_validate_json(cached_response)
            response_data.cached = True
            return response_data

        if self.should_be_rate_limited(input_text):
            logger.info("Rate limited")
            raise RateLimitedException("Rate limited")

        try:
            usable_ai_response = self.get_ai_response(input_text, model=MODEL_NAME)

            # Prepare response
            response_data = CacheResponse(
                input=input_text,
                model=MODEL_NAME,
                cache_version=CacheService.hash_category_key(),
                response=usable_ai_response,
                cached=False,
            )

            # Save to cache
            self.cache_service.save(hash_key, response_data.model_dump_json())

            self.lambda_client.invoke(
                FunctionName=os.environ['MULTISUMMARIZER_LAMBDA_NAME'],
                InvocationType='Event',
                Payload=json.dumps({'original_text_key': hash_key})
            )

            return response_data

        except Exception as e:
            logger.error(f"Error calling OpenAI API: {str(e)}", exc_info=True)
            logger.error(f"Input text length: {len(input_text)}")
            raise

    def should_be_rate_limited(self, input_text):
        file_name = hashlib.sha1(input_text.encode()).hexdigest()
        try:
            # Get the object from S3
            response = self.s3_client.get_object(
                Bucket=BUCKET_NAME_RATE_LIMIT, Key=file_name
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
                Key=file_name,
                Body=json.dumps({"LastModified": str(time.time())}),
                ContentType="application/json",
            )
        return should_be_rate_limited

    async def get_ai_response(self, input_text: str, model: str = MODEL_NAME) -> AlertSummaryContainer:
        summary_task = b.GetAlertSummary(input_text)
        delay_task = b.IsDelayAlert(input_text)
        relevance_task = b.IsRelevantAlert(input_text)
        affected_area_task = b.GetAffectedArea(input_text)

        summary, is_delay, is_relevant, affected_area = await asyncio.gather(
            summary_task,
            delay_task,
            relevance_task,
            affected_area_task
        )
        
        return AlertSummaryContainer(
            text=summary,
            is_delay=is_delay.is_delay,
            is_relevant=is_relevant.is_relevant,
            affected_area=affected_area
        )

    @staticmethod
    def user_msg(content: str``):
        return {"role": "user", "content": content}

    @staticmethod
    def assistant_msg(summary: AlertSummary):
        return {"role": "assistant", "content": summary.model_dump_json()}
