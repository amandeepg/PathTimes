import json
import os
from typing import Dict, Any
from aws_lambda_powertools import Logger, Tracer
from aws_lambda_powertools.utilities.typing import LambdaContext
from aws_lambda_powertools.utilities.data_classes import APIGatewayProxyEvent
from .lib.summarizer import AlertSummarizer

logger = Logger()
tracer = Tracer()


@logger.inject_lambda_context
@tracer.capture_lambda_handler
def handler(event: APIGatewayProxyEvent, context: LambdaContext) -> Dict[str, Any]:
    """Lambda handler for alert summarization."""
    logger.info("Received new request")
    logger.debug(f"Event: {json.dumps(event)}")

    try:
        # Get input string from event
        query_params = event.get("queryStringParameters", {})
        if not query_params or "input" not in query_params:
            logger.error("Missing input parameter in request")
            return {
                "statusCode": 400,
                "body": json.dumps({"error": "Missing input parameter"}),
            }

        input_text = query_params["input"]
        logger.info(f"Processing request with input length: {len(input_text)}")

        skip_cache = "skip_cache" in query_params and query_params[
            "skip_cache"
        ] == os.environ.get("SKIP_CACHE_MAGIC_WORD")

        summarizer = AlertSummarizer()
        result = summarizer.summarize(input_text, skip_cache=skip_cache)

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

    except Exception as e:
        logger.exception("Error processing request")
        return {
            "statusCode": 500,
            "body": json.dumps({"error": str(e)}),
            "headers": {"Content-Type": "application/json"},
        }
