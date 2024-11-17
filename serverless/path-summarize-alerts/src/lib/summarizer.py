import hashlib
from typing import Dict, Any
from openai import OpenAI
from instructor import from_openai, Mode
from aws_lambda_powertools import Logger, Tracer
from .models import AlertSummary
from .cache import CacheService
from .constants import BUCKET_NAME, CACHE_VERSION

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

logger = Logger()
tracer = Tracer()

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

        # Check lib
        cached_response = self.cache_service.get(hash_key) if not skip_cache else None
        if cached_response:
            logger.info(f"Cache hit for hash: {hash_key}")
            return {
                'response': cached_response['response'],
                'cached': True,
                'cache_version': CACHE_VERSION,
                'model': cached_response.get('model', MODEL_NAME)
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

            # Save to lib
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