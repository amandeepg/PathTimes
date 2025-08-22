import hashlib
from typing import Optional, List
from dyntastic import A

from aws_lambda_powertools import Logger
from botocore.exceptions import ClientError
from lib.models import AlertSummaryAiResponse

from .constants import CACHE_INT
from .hash_constants import LLM_HASH
from .llm_client_base import LlmClient

logger = Logger()


class CacheService:
    @staticmethod
    def hash_category_key() -> str:
        """Create an SHA-1 hash of the prompt."""
        return f"{LLM_HASH}-{CACHE_INT}"

    @staticmethod
    def hash_llm_key(input_string: str, model: LlmClient) -> str:
        """Create an SHA-1 hash of the input string."""
        input_hash_value = hashlib.sha1(input_string.encode("utf-8")).hexdigest()
        model_hash_value = hashlib.sha1(model.id().encode("utf-8")).hexdigest()
        hash_value = (
            f"{input_hash_value}/{model.id().replace('/', '--')}-{model_hash_value}"
        )
        logger.debug(f"Generated hash_key: {hash_value}")
        return hash_value

    @staticmethod
    def hash_key(input_string: str) -> str:
        """Create an SHA-1 hash of the input string."""
        input_hash_value = hashlib.sha1(input_string.encode("utf-8")).hexdigest()
        return input_hash_value

    @staticmethod
    def create_versioned_key(hash_key: str, hash_category_key: str) -> str:
        """Create a versioned lib key."""
        return f"{hash_category_key}/{hash_key}"

    def get(
        self, hash_key: str, hash_category_key: Optional[str] = None
    ) -> Optional[AlertSummaryAiResponse]:
        """Try to get cached response from DynamoDB."""
        if hash_category_key is None:
            hash_category_key = CacheService.hash_category_key()
        versioned_key = self.create_versioned_key(hash_key, hash_category_key)
        logger.debug(
            f"Attempting to retrieve cached response for versioned key: {versioned_key}"
        )
        try:
            # Query the most recent item for this hash_key
            response: AlertSummaryAiResponse = list(
                AlertSummaryAiResponse.query(  # pyright: ignore[reportUnknownMemberType]
                    hash_key,
                    filter_condition=A.code_version_hash == hash_category_key,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType, reportArgumentType]
                )
            )[0]
            logger.debug(f"Cache data: {response.model_dump()}")
            return response

        except ClientError as e:
            logger.error(f"Error retrieving from DynamoDB: {str(e)}", exc_info=True)
            logger.error(f"Hash key: {versioned_key}")
            return None
        except Exception as e:
            logger.error(f"Error retrieving from lib: {str(e)}", exc_info=True)
            logger.error(f"Hash key: {versioned_key}")
            return None

    def save(self, data: AlertSummaryAiResponse) -> None:
        """Save response to DynamoDB."""
        data.save()

    def query_all(self, code_version_hash: str) -> List[AlertSummaryAiResponse]:
        """Query all items by code_version_hash prefix."""
        try:
            return list(
                AlertSummaryAiResponse.query(  # pyright: ignore[reportUnknownMemberType]
                    A.code_version_hash == code_version_hash,
                    index="CodeVersionIndex",
                    load_full_item=True,
                )
            )  # pyright: ignore[reportUnknownMemberType]
        except Exception as e:
            logger.error(
                f"Error querying by hash_category_key: {str(e)}", exc_info=True
            )
            return []
