import json
import boto3
from typing import Optional, Dict, Any
from aws_lambda_powertools import Logger, Tracer
from .constants import CACHE_VERSION

logger = Logger()
tracer = Tracer()

class CacheService:
    def __init__(self, bucket_name: str):
        self.s3_client = boto3.client('s3')
        self.bucket_name = bucket_name
        logger.info(f"Initialized CacheService with bucket: {bucket_name}")

    @staticmethod
    def create_versioned_key(hash_key: str) -> str:
        """Create a versioned lib key."""
        return f"{CACHE_VERSION}/{hash_key}"

    @tracer.capture_method
    def get(self, hash_key: str) -> Optional[Dict[str, Any]]:
        """Try to get cached response from S3."""
        versioned_key = self.create_versioned_key(hash_key)
        logger.debug(f"Attempting to retrieve cached response for versioned key: {versioned_key}")
        try:
            response = self.s3_client.get_object(Bucket=self.bucket_name, Key=versioned_key)
            data = json.loads(response['Body'].read().decode('utf-8'))
            logger.info(f"Successfully retrieved cached response for versioned key: {versioned_key}")
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
    def save(self, hash_key: str, data: Dict[str, Any]) -> None:
        """Save response to S3 lib."""
        versioned_key = self.create_versioned_key(hash_key)
        logger.debug(f"Attempting to lib response for versioned key: {versioned_key}")
        try:
            self.s3_client.put_object(
                Bucket=self.bucket_name,
                Key=versioned_key,
                Body=json.dumps(data),
                ContentType='application/json'
            )
            logger.info(f"Successfully cached response for versioned key: {versioned_key}")
            logger.debug(f"Cached data: {json.dumps(data)}")
        except Exception as e:
            logger.error(f"Error saving to lib: {str(e)}", exc_info=True)
            logger.error(f"Versioned key: {versioned_key}, Bucket: {self.bucket_name}")