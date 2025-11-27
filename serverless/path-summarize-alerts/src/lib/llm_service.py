from decimal import Decimal
import os

from aws_lambda_powertools import Logger
from baml_py import ClientRegistry, Collector
from opentelemetry import trace

from baml_client import b
from baml_client.types import (
    AlertSummary,
    DateAndTime,
    RedBullArenaInfo,
)

from .llm_client_base import LlmClient
from .models import AffectedLines, AffectedStations, LlmCostInfo, LlmResponseWithCost
from .llm_programs.affected_area import AffectedAreaDeterminer
from .llm_programs.elevator_affected_area import ElevatorAffectedStationsDeterminer

logger = Logger()
tracer = trace.get_tracer(__name__)


class LlmService:
    """Service class to encapsulate all LLM interactions."""

    def __init__(self):
        logger.info("Initialized LlmService")
        self.affected_area_determiner = AffectedAreaDeterminer()
        self.elevator_affected_area_determiner = ElevatorAffectedStationsDeterminer()

    @tracer.start_as_current_span("llm.get_affected_area")
    async def get_affected_area(
        self, input_text: str
    ) -> LlmResponseWithCost[tuple[AffectedStations | None, AffectedLines | None]]:
        if "elevator" in input_text or "Elevator" in input_text:
            affected_lines = None
            affected_stations = (
                self.elevator_affected_area_determiner.determine_affected_stations(
                    input_text
                )
            )
        else:
            affected_lines, affected_stations = (
                self.affected_area_determiner.determine_affected_area(input_text)
            )

        # TODO: DSPy doesn't have cost tracking, so we return zero cost
        total_cost = Decimal(0.0)
        logger.info(f"LLM call cost for GetAffectedArea: {total_cost}")

        return LlmResponseWithCost(
            response=(affected_stations, affected_lines),
            cost_info=LlmCostInfo(total_cost=total_cost),
        )

    @tracer.start_as_current_span("llm.remove_single_line_or_route_from_summary")
    async def remove_single_line_or_route_from_summary(
        self,
        cr: ClientRegistry,
        input_text: str,
        single_area: str,
    ) -> LlmResponseWithCost[AlertSummary]:
        collector = Collector()
        response = await b.RemoveSingleLineOrRouteFromSummary(
            input_text,
            single_area,
            baml_options={"client_registry": cr, "collector": collector},
        )

        total_cost = self._calculate_cost(collector)
        logger.info(
            f"LLM call cost for RemoveSingleLineOrRouteFromSummary: {total_cost}"
        )

        return LlmResponseWithCost(
            response=response, cost_info=LlmCostInfo(total_cost=total_cost)
        )

    @tracer.start_as_current_span("llm.get_alert_summary")
    async def get_alert_summary(
        self, cr: ClientRegistry, input_text: str
    ) -> LlmResponseWithCost[AlertSummary]:
        collector = Collector()
        response = await b.GetAlertSummary(
            input_text, baml_options={"client_registry": cr, "collector": collector}
        )

        total_cost = self._calculate_cost(collector)
        logger.info(f"LLM call cost for GetAlertSummary: {total_cost}")

        return LlmResponseWithCost(
            response=response, cost_info=LlmCostInfo(total_cost=total_cost)
        )

    @tracer.start_as_current_span("llm.get_red_bull_arena_date_time")
    async def get_red_bull_arena_date_time(
        self, cr: ClientRegistry, input_text: str
    ) -> LlmResponseWithCost[DateAndTime]:
        collector = Collector()
        response = await b.GetRedBullsArenaDateTime(
            input_text, baml_options={"client_registry": cr, "collector": collector}
        )

        total_cost = self._calculate_cost(collector)
        logger.info(f"LLM call cost for GetRedBullsArenaDateTime: {total_cost}")

        return LlmResponseWithCost(
            response=response, cost_info=LlmCostInfo(total_cost=total_cost)
        )

    @tracer.start_as_current_span("llm.get_red_bull_arena_info")
    async def get_red_bull_arena_info(
        self, input_text: str
    ) -> LlmResponseWithCost[RedBullArenaInfo]:
        cr = ClientRegistry()
        cr.add_llm_client(
            name="perplexity",
            provider="openai-generic",
            options={
                "model": "perplexity/sonar",
                "temperature": 0.0,
                "api_key": os.environ.get("OPENROUTER_API_KEY"),
                "base_url": "https://openrouter.ai/api/v1",
                "headers": {
                    "HTTP-Referer": os.environ.get("OPENROUTER_APP_URL"),
                    "X-Title": os.environ.get("OPENROUTER_APP_NAME"),
                },
            },
        )
        cr.set_primary("perplexity")

        collector = Collector()
        response = await b.GetRedBullsArenaText(
            input_text, baml_options={"client_registry": cr, "collector": collector}
        )

        total_cost = self._calculate_cost(collector)
        logger.info(f"LLM call cost for GetRedBullsArenaText: {total_cost}")

        return LlmResponseWithCost(
            response=response, cost_info=LlmCostInfo(total_cost=total_cost)
        )

    @staticmethod
    def create_client_registry(model: LlmClient) -> ClientRegistry:
        cr = ClientRegistry()
        model.add_to_registry(cr)
        cr.set_primary(model.id())
        return cr

    @staticmethod
    def _calculate_cost(collector: Collector) -> Decimal:
        total_cost = Decimal(0.0)
        if collector.logs:
            for log in collector.logs:
                if log.calls:
                    for call in log.calls:  # pyright: ignore[reportOptionalMemberAccess]
                        try:
                            resp = call.http_response
                            if not resp:
                                continue
                            cost_str = resp.body.json()["usage"]["cost"]
                            if not cost_str:
                                continue
                            total_cost += Decimal(cost_str)
                        except (AttributeError, KeyError, TypeError):
                            continue
        return total_cost
