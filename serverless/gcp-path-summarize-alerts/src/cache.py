import hashlib
from collections.abc import Mapping
from datetime import datetime, timedelta
from typing import NamedTuple, cast

from google.cloud import firestore  # pyright: ignore[reportMissingTypeStubs]
from pydantic import ValidationError

from .models import AlertSummaryContainer

CHEAP_COLLECTION_NAME = "summaries_cheap"
EXPENSIVE_COLLECTION_NAME = "summaries_expensive"


class CacheHit(NamedTuple):
    data: dict[str, object]
    ttl_seconds: int


def hash_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


class FirestoreCache:
    """Encapsulates Firestore cache interactions for cheap/expensive summaries."""

    _client: firestore.Client
    _cheap_collection: str
    _expensive_collection: str
    _location: str
    _cheap_ttl_seconds: int
    _expensive_ttl_seconds: int

    def __init__(
        self,
        client: firestore.Client,
        *,
        cheap_collection: str,
        expensive_collection: str,
        location: str,
        cheap_ttl_seconds: int,
        expensive_ttl_seconds: int,
    ) -> None:
        self._client = client
        self._cheap_collection = cheap_collection
        self._expensive_collection = expensive_collection
        self._location = location
        self._cheap_ttl_seconds = cheap_ttl_seconds
        self._expensive_ttl_seconds = expensive_ttl_seconds

    @property
    def cheap_ttl_seconds(self) -> int:
        return self._cheap_ttl_seconds

    def get_expensive(self, cache_key: str, now: datetime) -> CacheHit | None:
        return self._get(self._expensive_collection, cache_key, now)

    def get_cheap(self, cache_key: str, now: datetime) -> CacheHit | None:
        return self._get(self._cheap_collection, cache_key, now)

    def store_cheap(
        self,
        cache_key: str,
        *,
        text: str,
        result: AlertSummaryContainer,
        llm_type: str,
        now: datetime,
    ) -> None:
        expires_at = now + timedelta(seconds=self._cheap_ttl_seconds)
        self._store(
            self._cheap_collection, cache_key, text, result, llm_type, now, expires_at
        )

    def store_expensive(
        self,
        cache_key: str,
        *,
        text: str,
        result: AlertSummaryContainer,
        llm_type: str,
        now: datetime,
    ) -> None:
        expires_at = now + timedelta(seconds=self._expensive_ttl_seconds)
        self._store(
            self._expensive_collection,
            cache_key,
            text,
            result,
            llm_type,
            now,
            expires_at,
        )

    def _get(self, collection: str, cache_key: str, now: datetime) -> CacheHit | None:
        doc_ref: firestore.DocumentReference = self._client.collection(
            collection
        ).document(cache_key)
        doc: firestore.DocumentSnapshot = doc_ref.get()  # pyright: ignore[reportUnknownMemberType]
        if not doc.exists:
            return None
        data = cast(dict[str, object], doc.to_dict() or {})
        expires_at = data.get("expires_at")
        if not isinstance(expires_at, datetime):
            return None
        remaining = int((expires_at - now).total_seconds())
        if remaining <= 0:
            return None
        return CacheHit(data=data, ttl_seconds=remaining)

    def _store(
        self,
        collection: str,
        cache_key: str,
        text: str,
        result: AlertSummaryContainer,
        llm_type: str,
        created_at: datetime,
        expires_at: datetime,
    ) -> None:
        doc_ref: firestore.DocumentReference = self._client.collection(
            collection
        ).document(cache_key)
        _ = doc_ref.set(  # pyright: ignore[reportUnknownMemberType]
            {
                "text": text,
                "summary": result.text,
                "result": self.serialize_result(result),
                "llm_type": llm_type,
                "location": self._location,
                "created_at": created_at,
                "expires_at": expires_at,
            }
        )

    @staticmethod
    def load_result(data: Mapping[str, object]) -> AlertSummaryContainer | None:
        result_obj = data.get("result")
        if isinstance(result_obj, dict):
            try:
                return AlertSummaryContainer.model_validate(result_obj)
            except ValidationError:
                pass
        summary_text = data.get("summary")
        if isinstance(summary_text, str) and summary_text:
            return AlertSummaryContainer(
                text=summary_text,
                affected_stations=None,
                affected_lines=None,
                summary_cost=None,
                affected_area_cost=None,
            )
        return None

    @staticmethod
    def serialize_result(result: AlertSummaryContainer) -> dict[str, object]:
        return result.model_dump(mode="json")
