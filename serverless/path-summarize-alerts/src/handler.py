import json
import hashlib
import os

import boto3
from typing import Optional, Dict, Any
from openai import OpenAI
from instructor import from_openai, Mode
from pydantic import BaseModel, Field
from aws_lambda_powertools import Logger, Tracer
from aws_lambda_powertools.utilities.typing import LambdaContext
from aws_lambda_powertools.utilities.data_classes import APIGatewayProxyEvent

# Initialize utilities
logger = Logger()
tracer = Tracer()

# Constants
BUCKET_NAME = 'path-summarize-data'
CACHE_VERSION = '1.0'
MODEL_NAME = "gpt-4o"
SYSTEM_MESSAGE = """
Take alert text from a transit agency and make it more digestible for transit riders. 
Shorten the text by omitting unnecessary fluff and politeness, assuming the rider is an experienced traveler. 
References to PATH should be removed if implied, keeping in mind that the target audience is familiar with PATH and 
utilizes an app exclusive to this transit line.
Represent dates using American formats with month names, like January 26th or December 2nd.
Expand the station name from the abbreviation to the full name, except when speaking about lines. Lines are formatted like JSQ-33 or NWK-WTC.
Common abbreviations:
<abbreviations>
"JSQ" for "Journal Square"
"NWK" for "Newark"
"GRV" for "Grove Street"
"HAR" for "Harrison"
"EXP" for "Exchange Place"
"WTC" for "World Trade Center"
"HOB" for "Hoboken"
"NWPT" for "Newport"
"CHRS" for "Christopher Street"
"Chris St" for "Christopher Street"
"33" for "33rd Street"
</abbreviations>
"
"""

class AlertSummary(BaseModel):
    text: str
    is_delay: bool = Field(description="indicating if the alert is about a delay on lines, true is yes, false if it is a general announcement")
    is_relevant: bool = Field(description="indicating if the alert affects the rider's experience or not")

class CacheService:
    def __init__(self, bucket_name: str):
        self.s3_client = boto3.client('s3')
        self.bucket_name = bucket_name
        logger.info(f"Initialized CacheService with bucket: {bucket_name}")

    @staticmethod
    def create_versioned_key(hash_key: str) -> str:
        """Create a versioned cache key."""
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
            logger.error(f"Error retrieving from cache: {str(e)}", exc_info=True)
            logger.error(f"Versioned key: {versioned_key}, Bucket: {self.bucket_name}")
            return None

    @tracer.capture_method
    def save(self, hash_key: str, data: Dict[str, Any]) -> None:
        """Save response to S3 cache."""
        versioned_key = self.create_versioned_key(hash_key)
        logger.debug(f"Attempting to cache response for versioned key: {versioned_key}")
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
            logger.error(f"Error saving to cache: {str(e)}", exc_info=True)
            logger.error(f"Versioned key: {versioned_key}, Bucket: {self.bucket_name}")

class AlertSummarizer:
    def __init__(self):
        self.client = from_openai(OpenAI(), mode=Mode.TOOLS_STRICT)
        self.cache_service = CacheService(BUCKET_NAME)
        logger.info("Initialized AlertSummarizer")

    @staticmethod
    def hash_string(input_string: str) -> str:
        """Create a SHA-256 hash of the input string."""
        hash_value = hashlib.sha256(input_string.encode('utf-8')).hexdigest()
        logger.debug(f"Generated hash: {hash_value} for input length: {len(input_string)}")
        return hash_value

    @tracer.capture_method
    def summarize(self, input_text: str, skip_cache: bool) -> Dict[str, Any]:
        """Summarize the input text using OpenAI API with caching."""
        logger.info(f"Processing new summarization request. Input length: {len(input_text)}")
        logger.debug(f"Raw input text: {input_text}")

        hash_key = self.hash_string(input_text)

        # Check cache
        cached_response = self.cache_service.get(hash_key) if not skip_cache else None
        if cached_response:
            logger.info(f"Cache hit for hash: {hash_key}")
            return {
                'response': cached_response['response'],
                'cached': True,
                'cache_version': CACHE_VERSION,
                'model': cached_response.get('model', MODEL_NAME)  # Fallback for older cached responses
            }

        # Call OpenAI API
        try:
            logger.info("Making OpenAI API request")
            logger.debug(f"System message: {SYSTEM_MESSAGE}")

            ai_response, completion = self.client.chat.completions.create_with_completion(
                response_model=AlertSummary,
                model=MODEL_NAME,
                messages=[
                    {"role": "system", "content": SYSTEM_MESSAGE},
                    self.user_msg(
                        "PATH's debit/credit system for SmartLink/MetroCard sales out of service 07-13-2024 "
                        "2:00am-8:00am due to system maintenance. Use contactless debit/credit at TAPP turnstiles or "
                        "use cash for fare purchases. Use NYCT Subway stations for new MetroCard purchases with "
                        "debit/credit",
                    ),
                    self.assistant_msg(
                        AlertSummary(
                            text = "System for SmartLink/MetroCard sales with credit/debit cards will be out of "
                                   "service on August 13th 2am-8am. Instead use contactless tap-to-pay at turnstiles, "
                                   "cash for fare purchases, or purchase MetroCards at an NYC Subway station.",
                            is_delay = False,
                            is_relevant = True,
                        )
                    ),
                    self.user_msg(
                        "Red Bull Arena event Sat 08-31-2024 at 7:30pm. Extra svc after event. "
                        "RBA is steps from Harrison Station. "
                        "Tips: "
                        "- Allow extra travel time to get to RBA. "
                        "- Pay fare at TAPP turnstile w mobile wallet or contactless bank "
                        "  card (http://www.TAPPandRide.com) OR buy fare in advance."
                    ),
                    self.assistant_msg(
                        AlertSummary(
                            text="There is an event at Red Bull Arena on August 31st at 7:30pm, near Harrison Station. "
                                 "Extra service provided after event. It is suggested to provide extra travel time, "
                                 "use tap-to-pay at turnstiles or buy fares in advance.",
                            is_delay=False,
                            is_relevant=True,
                        )
                    ),
                    self.user_msg(input_text)
                ],
            )

            logger.info("Successfully received OpenAI API response")
            logger.debug(f"Raw API response: {ai_response}")
            logger.info(f"Usage: {completion.usage}")
            cost = completion.usage.prompt_tokens * 2.5 / 1_000_000 + completion.usage.completion_tokens * 10 / 1_000_000
            logger.info(f"Cost approx: {cost * 100} cents")

            # Prepare response
            response_data = {
                'input': input_text,
                'response': dict(ai_response),
                'model': MODEL_NAME,
                'cache_version': CACHE_VERSION
            }

            # Save to cache
            self.cache_service.save(hash_key, response_data)

            return {
                'response': dict(ai_response),
                'input': input_text,
                'cached': False,
                'cache_version': CACHE_VERSION,
                'model': MODEL_NAME
            }

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


@logger.inject_lambda_context
@tracer.capture_lambda_handler
def handler(event: APIGatewayProxyEvent, context: LambdaContext) -> Dict[str, Any]:
    """Lambda handler for alert summarization."""
    logger.info("Received new request")
    logger.debug(f"Event: {json.dumps(event)}")

    try:
        # Get input string from event
        query_params = event.get('queryStringParameters', {})
        if not query_params or 'input' not in query_params:
            logger.error("Missing input parameter in request")
            return {
                'statusCode': 400,
                'body': json.dumps({'error': 'Missing input parameter'})
            }

        input_text = query_params['input']
        logger.info(f"Processing request with input length: {len(input_text)}")

        skip_cache = 'skip_cache' in query_params and query_params['skip_cache'] == os.environ.get("SKIP_CACHE_MAGIC_WORD")

        summarizer = AlertSummarizer()
        result = summarizer.summarize(input_text, skip_cache=skip_cache)

        logger.info("Successfully processed request")
        logger.debug(f"Response: {json.dumps(result)}")

        return {
            'statusCode': 200,
            'body': json.dumps(result),
            'headers': {
                'Content-Type': 'application/json',
                'Cache-Control': 'max-age=86400'  # Cache for 24 hours
            }
        }

    except Exception as e:
        logger.exception("Error processing request")
        return {
            'statusCode': 500,
            'body': json.dumps({
                'error': str(e)
            }),
            'headers': {
                'Content-Type': 'application/json'
            }
        }