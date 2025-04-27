from pydantic import BaseModel
from baml_client.types import AffectedStations, AffectedRoutes


class AlertSummaryContainer(BaseModel):
    text: str
    is_delay: bool
    affected_area: AffectedStations | AffectedRoutes | None


class CacheResponse(BaseModel):
    input: str
    response: AlertSummaryContainer
    model: str
    cache_version: str
    cached: bool
    generated_at: int
    hash_key: str
