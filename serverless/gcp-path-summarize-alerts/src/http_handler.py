import json
from collections.abc import Mapping
from datetime import datetime, timezone
import logging
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
from .settings import (
    CHEAP_TTL_SECONDS,
    ENVIRONMENT,
    LOCATION,
    EXPENSIVE_TTL_SECONDS,
    CACHE_KEY_PREFIX,
    PRO_QUEUE_ID,
    PRO_WORKER_URL,
    PROJECT_ID,
)
from .models import SummarizeRequest, SummarizeResponse
from .observability import request_logger
from .tasks import enqueue_pro_task

DB: firestore.Client = firestore.Client(project=PROJECT_ID)
CACHE = FirestoreCache(
    DB,
    cheap_collection=CHEAP_COLLECTION_NAME,
    expensive_collection=EXPENSIVE_COLLECTION_NAME,
    location=LOCATION,
    cheap_ttl_seconds=CHEAP_TTL_SECONDS,
    expensive_ttl_seconds=EXPENSIVE_TTL_SECONDS,
)
_CHEAP_SUMMARIZER = LlmSummarizer(LlmType.FAST)


def _json_response(
    body: Mapping[str, object], status: int = 200, max_age: int | None = None
) -> tuple[str, int, dict[str, str]]:
    headers = {"Content-Type": "application/json"}
    if max_age is not None:
        headers["Cache-Control"] = f"public, max-age={max_age}"
    return json.dumps(body), status, headers


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _build_cache_key(text: str, prefixes: tuple[str | None, ...]) -> str:
    base_key = hash_text(text)
    valid_prefixes = [p for p in prefixes if p]
    if not valid_prefixes:
        return base_key
    combined_prefix = ":".join(valid_prefixes)
    return f"{combined_prefix}:{base_key}"


def summarize_http(request: Request) -> tuple[str, int, dict[str, str]]:
    logger = request_logger(request, name="http")
    try:
        start_total = perf_counter()
        logger.info(
            "summarize_request_start %s",
            {"path": request.path, "method": request.method},
        )
        parsed = _parse_request(request)
        if parsed is None:
            logger.info("summarize_request_invalid %s", {"reason": "missing_text"})
            return _json_response(
                {"error": "Provide non-empty 'text' in JSON body or query parameter."},
                400,
            )
        text = parsed.text
        cache_key_prefix = parsed.cache_key_prefix

        now = _now()
        cache_key = _build_cache_key(text, (CACHE_KEY_PREFIX, cache_key_prefix))

        logger.info(
            "cache_lookup_expensive %s",
            {
                "cache_key": cache_key,
                "text_length": len(text),
                "cache_key_prefix": cache_key_prefix or "",
            },
        )
        expensive_hit = CACHE.get_expensive(cache_key, now)

        if expensive_hit:
            data = expensive_hit.data
            remaining = expensive_hit.ttl_seconds
            result = FirestoreCache.load_result(data)
            logger.info(
                "cache_hit_expensive %s",
                {
                    "cache_key": cache_key,
                    "ttl_seconds": remaining,
                    "cached": True,
                    "llm_type": data.get("llm_type") or data.get("model") or "unknown",
                },
            )
            response_body = SummarizeResponse(
                input=text,
                summary=result.text if result else cast(str, data.get("summary", "")),
                result=result,
                cached=True,
                llm_type=cast(
                    str, data.get("llm_type") or data.get("model") or "unknown"
                ),
                environment=ENVIRONMENT,
                ttl_seconds=remaining,
            ).model_dump(mode="json")
            return _json_response(response_body, 200, max_age=remaining)

        logger.info("cache_miss_expensive %s", {"cache_key": cache_key})

        worker_url = PRO_WORKER_URL or _worker_url_from_request(request)
        enqueue_pro_task(
            text=text,
            cache_key=cache_key,
            project_id=PROJECT_ID,
            region=LOCATION,
            queue_id=PRO_QUEUE_ID,
            worker_url=worker_url,
            logger=logger,
        )
        logger.info(
            "pro_task_enqueue_requested %s",
            {
                "cache_key": cache_key,
                "queue_id": PRO_QUEUE_ID,
                "worker_url": worker_url,
            },
        )

        logger.info("cache_lookup_cheap %s", {"cache_key": cache_key})
        cheap_hit = CACHE.get_cheap(cache_key, now)
        if cheap_hit:
            data = cheap_hit.data
            remaining = cheap_hit.ttl_seconds
            result = FirestoreCache.load_result(data)
            logger.info(
                "cache_hit_cheap %s",
                {
                    "cache_key": cache_key,
                    "ttl_seconds": remaining,
                    "cached": True,
                    "llm_type": data.get("llm_type")
                    or data.get("model")
                    or LlmType.FAST.value,
                    "upgrading_to_expensive": True,
                },
            )
            response_body = SummarizeResponse(
                input=text,
                summary=result.text if result else cast(str, data.get("summary", "")),
                result=result,
                cached=True,
                llm_type=cast(
                    str, data.get("llm_type") or data.get("model") or LlmType.FAST.value
                ),
                environment=ENVIRONMENT,
                ttl_seconds=remaining,
                upgrading_to_expensive=True,
            ).model_dump(mode="json")
            return _json_response(response_body, 200, max_age=remaining)

        logger.info("cache_miss_cheap %s", {"cache_key": cache_key})
        logger.info(
            "cheap_summarize_start %s",
            {"cache_key": cache_key, "llm_type": LlmType.FAST.value},
        )
        result = _CHEAP_SUMMARIZER.summarize(text, logger=logger)
        CACHE.store_cheap(
            cache_key, text=text, result=result, llm_type=LlmType.FAST.value, now=now
        )

        logger.info(
            "cache_store_cheap %s",
            {
                "cache_key": cache_key,
                "ttl_seconds": CACHE.cheap_ttl_seconds,
                "llm_type": LlmType.FAST.value,
            },
        )
        duration = perf_counter() - start_total
        logger.info(
            "request_complete %s",
            {
                "cache_key": cache_key,
                "duration_s": round(duration, 6),
                "llm_type": LlmType.FAST.value,
            },
        )
        response_body = SummarizeResponse(
            input=text,
            summary=result.text,
            result=result,
            cached=False,
            llm_type=LlmType.FAST.value,
            environment=ENVIRONMENT,
            ttl_seconds=CACHE.cheap_ttl_seconds,
            upgrading_to_expensive=True,
        ).model_dump(mode="json")
        return _json_response(response_body, 200, max_age=CACHE.cheap_ttl_seconds)
    except Exception as exc:  # pylint: disable=broad-except
        logging.getLogger("http").exception("Unhandled error")
        return _json_response({"error": str(exc)}, 500)


def _parse_request(request: Request) -> SummarizeRequest | None:
    text: str | None = None
    cache_key_prefix: str | None = None
    if request.is_json:
        payload_raw = request.get_json(silent=True)
        payload: dict[str, object] | None = cast(
            dict[str, object] | None,
            payload_raw if isinstance(payload_raw, dict) else None,
        )
        if payload:
            maybe_text = payload.get("text")
            if isinstance(maybe_text, str):
                text = maybe_text
            maybe_prefix = payload.get("cache_key_prefix")
            if isinstance(maybe_prefix, str) and maybe_prefix:
                cache_key_prefix = maybe_prefix
    if text is None and request.args:
        text = request.args.get("text")
        maybe_prefix = request.args.get("cache_key_prefix")
        if isinstance(maybe_prefix, str) and maybe_prefix:
            cache_key_prefix = maybe_prefix
    if not text:
        return None
    return SummarizeRequest(text=text, cache_key_prefix=cache_key_prefix)


def _worker_url_from_request(request: Request) -> str:
    """Builds a worker URL from the current request host."""
    scheme = "https"
    host = request.host or ""
    if host:
        return f"{scheme}://{host}/pro-worker"
    host_url = request.host_url or ""
    if not host_url:
        return "/pro-worker"
    return f"{scheme}://{host_url.split('://', 1)[-1].rstrip('/')}/pro-worker"
