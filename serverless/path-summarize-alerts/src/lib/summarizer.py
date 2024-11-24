import hashlib

from aws_lambda_powertools import Logger, Tracer
from instructor import from_openai
from openai import OpenAI

from .cache import CacheService
from .constants import BUCKET_NAME, SYSTEM_MESSAGE, MODEL_NAME
from .models import *
from .models import CacheResponse

logger = Logger()
tracer = Tracer()


class AlertSummarizer:
    def __init__(self):
        self.client = from_openai(OpenAI())  # , mode=Mode.TOOLS_STRICT)
        self.cache_service = CacheService(BUCKET_NAME)
        logger.info("Initialized AlertSummarizer")

    @staticmethod
    def hash_string(input_string: str) -> str:
        """Create a SHA-1 hash of the input string."""
        hash_value = hashlib.sha1(input_string.encode('utf-8')).hexdigest()
        logger.debug(f"Generated hash: {hash_value} for input length: {len(input_string)}")
        return hash_value

    @tracer.capture_method
    def summarize(self, input_text: str, skip_cache: bool) -> CacheResponse:
        """Summarize the input text using OpenAI API with caching."""
        logger.info(f"Processing new summarization request. Input length: {len(input_text)}")
        logger.debug(f"Raw input text: {input_text}")

        hash_key = self.hash_string(input_text)

        # Check lib
        cached_response = self.cache_service.get(hash_key) if not skip_cache else None
        if cached_response:
            logger.info(f"Cache hit for hash: {hash_key}")
            response_data = CacheResponse.model_validate_json(cached_response)
            response_data.cached = True
            return response_data

        # Call OpenAI API
        try:
            logger.info("Making OpenAI API request")
            logger.debug(f"System message: {SYSTEM_MESSAGE}")

            ai_response, completion = self.client.chat.completions.create_with_completion(
                response_model=AlertSummary,
                model=MODEL_NAME,
                messages=[
                    {"role": "system", "content": SYSTEM_MESSAGE},
                    self.user_msg(input_text)
                ],
            )

            logger.info("Successfully received OpenAI API response")
            logger.debug(f"Raw API response: {ai_response}")
            logger.info(f"Usage: {completion.usage}")
            cost = completion.usage.prompt_tokens * 2.5 / 1_000_000 + completion.usage.completion_tokens * 10 / 1_000_000
            logger.info(f"Cost approx: {cost * 100:.2f} cents")

            # Prepare response
            response_data = CacheResponse(
                input=input_text,
                model=MODEL_NAME,
                cache_version=CacheService.hash_category_key(),
                response=ai_response,
                cached=False,
            )

            # Save to lib
            self.cache_service.save(hash_key, response_data.model_dump_json())

            return response_data

        except Exception as e:
            logger.error(f"Error calling OpenAI API: {str(e)}", exc_info=True)
            logger.error(f"Input text length: {len(input_text)}")
            raise

    @staticmethod
    def user_msg(content: str):
        return {"role": "user", "content": content}

    @staticmethod
    def assistant_msg(summary: AlertSummary):
        return {"role": "assistant", "content": summary.model_dump_json()}
