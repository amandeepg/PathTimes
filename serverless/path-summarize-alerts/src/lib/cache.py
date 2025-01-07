import hashlib
import json
from typing import Optional

import boto3
from aws_lambda_powertools import Logger, Tracer

from .constants import SYSTEM_MESSAGE, MODEL_NAME, CACHE_INT
from .models import CacheResponse

logger = Logger()
tracer = Tracer()


class CacheService:
    def __init__(self, bucket_name: str):
        self.s3_client = boto3.client("s3")
        self.bucket_name = bucket_name
        logger.info(f"Initialized CacheService with bucket: {bucket_name}")

    @staticmethod
    def hash_category_key() -> str:
        """Create an SHA-1 hash of the prompt."""
        input_string = f"{CacheResponse.model_json_schema()}|{SYSTEM_MESSAGE}|{MODEL_NAME}|{CACHE_INT}"
        hash_value = hashlib.sha1(input_string.encode("utf-8")).hexdigest()
        return hash_value

    @staticmethod
    def create_versioned_key(hash_key: str) -> str:
        """Create a versioned lib key."""
        return f"{CacheService.hash_category_key()}/{hash_key}"

    @tracer.capture_method
    def get(self, hash_key: str) -> Optional[str]:
        """Try to get cached response from S3."""
        versioned_key = self.create_versioned_key(hash_key)
        logger.debug(
            f"Attempting to retrieve cached response for versioned key: {versioned_key}"
        )
        try:
            response = self.s3_client.get_object(
                Bucket=self.bucket_name, Key=versioned_key
            )
            data = response["Body"].read().decode("utf-8")
            logger.info(
                f"Successfully retrieved cached response for versioned key: {versioned_key}"
            )
            logger.debug(f"Cache data: {json.dumps(data)}")
            return data
        except self.s3_client.exceptions.NoSuchKey:
            logger.info(f"No cached response found for versioned key: {versioned_key}")
            return None
        except Exception as e:
            logger.error(f"Error retrieving from lib: {str(e)}", exc_info=True)
            logger.error(f"Versioned key: {versioned_key}, Bucket: {self.bucket_name}")
            return None

    @tracer.capture_method
    def save(self, hash_key: str, data: str) -> None:
        """Save response to S3 lib."""
        versioned_key = self.create_versioned_key(hash_key)
        logger.debug(f"Attempting to lib response for versioned key: {versioned_key}")
        try:
            self.s3_client.put_object(
                Bucket=self.bucket_name,
                Key=versioned_key,
                Body=data,
                ContentType="application/json",
            )
            logger.info(
                f"Successfully cached response for versioned key: {versioned_key}"
            )
            logger.debug(f"Cached data: {data}")
        except Exception as e:
            logger.error(f"Error saving to lib: {str(e)}", exc_info=True)
            logger.error(f"Versioned key: {versioned_key}, Bucket: {self.bucket_name}")
