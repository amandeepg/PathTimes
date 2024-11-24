from datetime import datetime
from enum import Enum
from typing import List
from typing import Optional

from pydantic import BaseModel, Field, field_serializer, field_validator


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
    NWK_WTC = "Newark - World Trade Center, stations along this route are Newark, Harrison, Journal Square, Grove Street, Exchange Place, World Trade Center"
    JSQ_WTC = "Journal Square - World Trade Center, stations along this route are Journal Square, Grove Street, Exchange Place, World Trade Center"
    HOB_WTC = "Hoboken - World Trade Center, stations along this route are Hoboken, Newport, Exchange Place, World Trade Center"
    JSQ_33 = "Journal Square - 33rd Street, stations along this route are Journal Square, Grove Street, Newport, Christopher Street, 9th Street, 14th Street, 23rd Street, 33rd Street"
    HOB_33 = "Hoboken - 33rd Street, stations along this route are Hoboken, Christopher Street, 9th Street, 14th Street, 23rd Street, 33rd Street"
    JSQ_33_HOB = "Journal Square - 33rd Street (via Hoboken), stations along this route are Journal Square, Grove Street, Newport, Hoboken, Christopher Street, 9th Street, 14th Street, 23rd Street, 33rd Street"


class TimeRange(BaseModel):
    chain_of_thought: str = Field(
        ..., description="Step by step reasoning to get the correct time range"
    )
    start_time: Optional[datetime] = Field(...,
                                           description="The date and time when the alert begins being relevant, if applicable")
    end_time: Optional[datetime] = Field(...,
                                         description="The date and time when the alert ends being relevant, if applicable")


class Summary(BaseModel):
    chain_of_thought: str = Field(
        ..., description="Step by step reasoning to get a summary of the alert"
    )
    text: str


class AffectedStations(BaseModel):
    chain_of_thought: str = Field(
        ...,
        description="Step by step reasoning to get the stations that are going to be affected by this alert, i.e. riders at this station really care about this alert"
    )
    affected_stations: Optional[List[PathStation]]


class AffectedRoutes(BaseModel):
    chain_of_thought: str = Field(
        ...,
        description="Step by step reasoning to get the routes that are going to be affected by this alert, i.e. riders on this line really care about this alert"
    )
    affected_routes: Optional[List[PathLine]]

    @field_serializer('affected_routes')
    def get_lines_value(self, routes: Optional[List[PathLine]]) -> Optional[List[str]]:
        if routes is None:
            return None
        return [route.name for route in routes]

    @field_validator('affected_routes', mode='before')
    @classmethod
    def validate_routes(cls, value: Optional[List[str]]) -> Optional[List[PathLine]]:
        if value is None:
            return None

        valid_routes = []
        for route in value:
            if isinstance(route, str):
                try:
                    # Try to match by name first
                    valid_routes.append(PathLine[route.upper()])
                except KeyError:
                    # If name doesn't match, try to match by value
                    try:
                        valid_routes.append(PathLine(route.lower()))
                    except ValueError:
                        raise ValueError(f"Invalid route: {route}. Valid routes are: {[e.name for e in PathLine]}")
            elif isinstance(route, PathLine):
                valid_routes.append(route)
            else:
                raise ValueError(f"Route must be string or PathLine enum, got {type(route)}")

        return valid_routes


class AlertSummary(BaseModel):
    text: Summary
    is_delay: bool = Field(
        description="indicating if the alert is about a delay on lines, true is yes, false if it is a general announcement")
    is_relevant: bool = Field(description="indicating if the alert affects the rider's experience or not")
    duration: Optional[TimeRange] = Field(
        description="The date and time that this alert begins and ends being applicable. Events at Red Bull Arena are usually 3 hours.")
    affected_routes: Optional[AffectedRoutes] = Field(description="List of affected routes")
    affected_stations: Optional[AffectedStations] = Field(description="List of affected stations")


class CacheResponse(BaseModel):
    input: str
    response: AlertSummary
    model: str
    cache_version: str
    cached: bool
