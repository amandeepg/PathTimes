from decimal import Decimal
import os
from typing import Generic, TypeVar
from typing import Any, List

from dyntastic import Dyntastic
from pydantic import BaseModel
from enum import Enum


class PathLine(str, Enum):
    """
    Enum representing PATH lines.
    """

    NWK_WTC = "NWK_WTC"
    JSQ_WTC = "JSQ_WTC"
    HOB_WTC = "HOB_WTC"
    JSQ_33 = "JSQ_33"
    HOB_33 = "HOB_33"
    JSQ_33_HOB = "JSQ_33_HOB"


class PathStation(str, Enum):
    """Enum representing PATH stations."""

    NWK = "Newark Penn Station"
    HAR = "Harrison"
    JSQ = "Journal Square"
    GRV = "Grove Street"
    EXP = "Exchange Place"
    WTC = "World Trade Center"
    HOB = "Hoboken"
    NEW = "Newport"
    CHR = "Christopher Street"
    S09 = "9th Street"
    S14 = "14th Street"
    S23 = "23rd Street"
    S33 = "33rd Street"


class AffectedLines(BaseModel):
    affected_lines: List[PathLine]


class AffectedStations(BaseModel):
    affected_stations: List[PathStation]


class AlertSummaryContainer(BaseModel):
    text: str
    is_delay: bool
    affected_stations: AffectedStations | None
    affected_lines: AffectedLines | None
    summary_cost: Decimal | None = None
    affected_area_cost: Decimal | None = None


class AlertSummaryAiResponse(Dyntastic):
    __table_name__ = lambda: os.getenv("DYNAMODB_TABLE")  # pyright: ignore[reportAssignmentType] # noqa: E731
    __hash_key__ = "input_string_hash"

    input_string_hash: str
    code_version_hash: str
    input: str
    response: AlertSummaryContainer
    model: str
    generated_at: int


class LlmCostInfo(BaseModel):
    total_cost: Decimal

    def __add__(self, other: "LlmCostInfo") -> "LlmCostInfo":
        return LlmCostInfo(
            total_cost=self.total_cost + other.total_cost,
        )


ResponseType = TypeVar("ResponseType")


class LlmResponseWithCost(BaseModel, Generic[ResponseType]):
    response: ResponseType
    cost_info: LlmCostInfo


def aggregate_llm_costs(
    responses: list[LlmResponseWithCost[Any] | None],
) -> LlmCostInfo:
    """Aggregate costs from a list of LlmResponseWithCost objects, ignoring None values."""
    total_cost = Decimal(0.0)

    for response in responses:
        if response is not None:
            total_cost += response.cost_info.total_cost

    return LlmCostInfo(total_cost=total_cost)
