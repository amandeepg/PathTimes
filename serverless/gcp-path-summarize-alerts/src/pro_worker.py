import json
from collections.abc import Mapping
from datetime import datetime, timezone
from time import perf_counter
from typing import cast

from flask import Request
from google.cloud import firestore  # pyright: ignore[reportMissingTypeStubs]

from .cache import (
    CHEAP_COLLECTION_NAME,
    EXPENSIVE_COLLECTION_NAME,
    FirestoreCache,
    hash_text,
)
from .llm import LlmSummarizer, LlmType
from .observability import request_logger
from .settings import (
    CHEAP_TTL_SECONDS,
    ENVIRONMENT,
    EXPENSIVE_TTL_SECONDS,
    LOCATION,
    PROJECT_ID,
)

DB: firestore.Client = firestore.Client(project=PROJECT_ID)
CACHE = FirestoreCache(
    DB,
    cheap_collection=CHEAP_COLLECTION_NAME,
    expensive_collection=EXPENSIVE_COLLECTION_NAME,
    location=LOCATION,
    cheap_ttl_seconds=CHEAP_TTL_SECONDS,
    expensive_ttl_seconds=EXPENSIVE_TTL_SECONDS,
)
_PRO_SUMMARIZER = LlmSummarizer(LlmType.SLOW)


def _json_response(
    body: Mapping[str, object], status: int = 200
) -> tuple[str, int, dict[str, str]]:
    return json.dumps(body), status, {"Content-Type": "application/json"}


def summarize_pro_worker(request: Request) -> tuple[str, int, dict[str, str]]:
    logger = request_logger(request, name="pro_worker")
    try:
        start_total = perf_counter()
        logger.info(
            "pro_worker_request_start %s",
            {"path": request.path, "method": request.method},
        )
        payload_raw = request.get_json(silent=True)
        payload: dict[str, object] | None = cast(
            dict[str, object] | None,
            payload_raw if isinstance(payload_raw, dict) else None,
        )
        text = payload.get("text") if payload else None
        if not isinstance(text, str) or not text:
            logger.info("pro_worker_request_invalid %s", {"reason": "missing_text"})
            return _json_response({"error": "missing text"}, 400)

        cache_key = cast(str, payload.get("cache_key")) if payload else hash_text(text)
        if not cache_key:
            cache_key = hash_text(text)

        logger.info("pro_worker_start %s", {"cache_key": cache_key})
        logger.info(
            "pro_worker_summarize_start %s",
            {"cache_key": cache_key, "llm_type": LlmType.SLOW.value},
        )
        summary = _PRO_SUMMARIZER.summarize(text, logger=logger)
        now = datetime.now(timezone.utc)
        CACHE.store_expensive(
            cache_key, text=text, result=summary, llm_type=LlmType.SLOW.value, now=now
        )
        logger.info(
            "cache_store_expensive %s",
            {
                "cache_key": cache_key,
                "ttl_seconds": EXPENSIVE_TTL_SECONDS,
                "llm_type": LlmType.SLOW.value,
            },
        )
        duration = perf_counter() - start_total
        logger.info(
            "pro_worker_complete %s",
            {
                "cache_key": cache_key,
                "duration_s": round(duration, 6),
                "llm_type": LlmType.SLOW.value,
            },
        )

        return _json_response(
            {
                "status": "ok",
                "llm_type": LlmType.SLOW.value,
                "environment": ENVIRONMENT,
                "ttl_seconds": EXPENSIVE_TTL_SECONDS,
            },
            200,
        )
    except Exception as exc:  # pylint: disable=broad-except
        return _json_response({"error": str(exc)}, 500)
