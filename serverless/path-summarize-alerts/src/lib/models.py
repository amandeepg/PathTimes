from enum import Enum
from typing import List
from typing import Optional

from pydantic import BaseModel, Field


class PathStation(Enum):
    NEWARK = "Newark Penn Station"
    HARRISON = "Harrison"
    JOURNAL_SQUARE = "Journal Square"
    GROVE_STREET = "Grove Street"
    EXCHANGE_PLACE = "Exchange Place"
    WORLD_TRADE_CENTER = "World Trade Center"
    HOBOKEN = "Hoboken"
    NEWPORT = "Newport"
    CHRISTOPHER_STREET = "Christopher Street"
    NINTH_STREET = "9th Street"
    FOURTEENTH_STREET = "14th Street"
    TWENTY_THIRD_STREET = "23rd Street"
    THIRTY_THIRD_STREET = "33rd Street"


class PathLine(Enum):
    NWK_WTC = "Newark - World Trade Center"
    JSQ_WTC = "Journal Square - World Trade Center"
    HOB_WTC = "Hoboken - World Trade Center"
    JSQ_33 = "Journal Square - 33rd Street"
    HOB_33 = "Hoboken - 33rd Street"
    JSQ_33_HOB = "Journal Square - 33rd Street (via Hoboken)"


class TimeRange(BaseModel):
    chain_of_thought: str = Field(
        ...,
        description="Step by step reasoning to get a summary of the alert, explain the reasoning for why each change to the text from the input was made.",
    )
    text: str


class AffectedStations(BaseModel):
    affected_stations: List[PathStation]


class AffectedRoutes(BaseModel):
    affected_routes: List[PathLine]


class IsDelay(BaseModel):
    chain_of_thought: str = Field(
        ...,
        description="Step by step reasoning to get if the alert is about a delay on lines.",
    )
    is_delay: bool


class IsRelevant(BaseModel):
    chain_of_thought: str = Field(
        ...,
        description="Step by step reasoning to get if the alert is indicating that it affects the rider's experience or not.",
    )
    is_relevant: bool


class AffectedArea(BaseModel):
    chain_of_thought: str = Field(
        ...,
        description="Step by step reasoning to get the area that is affected by the alert. Decide if the alert is affecting specific stations or a specific line. Decide if it more about the line or the stations irrespective of line. Only pick one. If it affects neither, pick neither.",
    )
    affected_area: Optional[AffectedStations | AffectedRoutes]


class Summary(BaseModel):
    chain_of_thought: str = Field(
        ..., description="Step by step reasoning to get a summary of the alert"
    )
    text: str


class AlertSummary(BaseModel):
    text: Summary
    is_delay: IsDelay = Field(
        description="indicating if the alert is about a delay on lines, true is yes, false if it is a general announcement"
    )
    is_relevant: IsRelevant = Field(
        description="indicating if the alert affects the rider's experience or not"
    )
    affected_area: AffectedArea = Field(
        description="List of affected stations or lines, but not both."
    )


class CacheResponse(BaseModel):
    input: str
    response: AlertSummary
    model: str
    cache_version: str
    cached: bool
