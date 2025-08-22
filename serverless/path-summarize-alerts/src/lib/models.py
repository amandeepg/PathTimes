import os
from dyntastic import Dyntastic
from pydantic import BaseModel
from baml_client.types import AffectedStations, AffectedRoutes


class AlertSummaryContainer(BaseModel):
    text: str
    is_delay: bool
    affected_area: AffectedStations | AffectedRoutes | None


class AlertSummaryAiResponse(Dyntastic):
    __table_name__ = lambda: os.getenv("DYNAMODB_TABLE")  # pyright: ignore[reportAssignmentType]
    __hash_key__ = "input_string_hash"

    input_string_hash: str
    code_version_hash: str
    input: str
    response: AlertSummaryContainer
    model: str
    generated_at: int
