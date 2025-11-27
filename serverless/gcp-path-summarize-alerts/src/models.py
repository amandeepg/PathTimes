from decimal import Decimal
from enum import Enum
from typing import ClassVar

from pydantic import BaseModel, ConfigDict


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
    model_config: ClassVar[ConfigDict] = ConfigDict(extra="ignore")

    affected_lines: list[PathLine]


class AffectedStations(BaseModel):
    model_config: ClassVar[ConfigDict] = ConfigDict(extra="ignore")

    affected_stations: list[PathStation]


class AlertSummaryContainer(BaseModel):
    model_config: ClassVar[ConfigDict] = ConfigDict(extra="ignore")

    text: str
    affected_stations: AffectedStations | None
    affected_lines: AffectedLines | None
    summary_cost: Decimal | None = None
    affected_area_cost: Decimal | None = None


class SummarizeRequest(BaseModel):
    text: str
    cache_key_prefix: str | None = None


class SummarizeResponse(BaseModel):
    input: str
    summary: str
    result: AlertSummaryContainer | None
    cached: bool
    llm_type: str
    environment: str
    ttl_seconds: int
    upgrading_to_expensive: bool | None = None
