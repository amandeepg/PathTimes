import asyncio
from decimal import Decimal
import time
from enum import Enum, auto

from aws_lambda_powertools import Logger
from baml_py import ClientRegistry
from opentelemetry import trace

from baml_client.types import (
    AlertSummary,
)

from .cache import CacheService
from .llm_client_base import LlmClient
from .llm_clients import FAST_LLM, PREFERRED_LLM
from .llm_service import LlmService
from .models import (
    AffectedStations,
    AlertSummaryAiResponse,
    AlertSummaryContainer,
    LlmResponseWithCost,
    PathLine,
    PathStation,
    aggregate_llm_costs,
)

logger = Logger()
tracer = trace.get_tracer(__name__)

# Define mappings using descriptions from path.baml
PATH_STATION_DESCRIPTIONS = {
    PathStation.NWK: "Newark Penn Station",
    PathStation.HAR: "Harrison",
    PathStation.JSQ: "Journal Square",
    PathStation.GRV: "Grove Street",
    PathStation.EXP: "Exchange Place",
    PathStation.WTC: "World Trade Center",
    PathStation.HOB: "Hoboken",
    PathStation.NEW: "Newport",
    PathStation.CHR: "Christopher Street",
    PathStation.S09: "9th Street",
    PathStation.S14: "14th Street",
    PathStation.S23: "23rd Street",
    PathStation.S33: "33rd Street",
}

PATH_LINE_DESCRIPTIONS = {
    PathLine.NWK_WTC: "NWK-WTC",
    PathLine.JSQ_WTC: "JSQ-WTC",
    PathLine.HOB_WTC: "HOB-WTC",
    PathLine.JSQ_33: "JSQ-33",
    PathLine.HOB_33: "HOB-33",
    PathLine.JSQ_33_HOB: "JSQ-33 via HOB",
}


class CachingPolicy(Enum):
    """Controls caching behavior for different operational scenarios.

    CACHE_ONLY: Used for debugging - only return cached results, never call LLMs
    NO_CACHE: Used for testing - always call LLMs, bypass all caching
    USE_CACHING: Default behavior - check cache first, then call LLM if needed"""

    CACHE_ONLY = auto()
    NO_CACHE = auto()
    USE_CACHING = auto()


class AlertSummarizer:
    def __init__(self):
        self._cache_service = CacheService()
        self._llm_service = LlmService()
        self._in_memory_cache: dict[tuple[str, str], AlertSummaryAiResponse] = {}
        logger.info("Initialized AlertSummarizer")

    @tracer.start_as_current_span("summ.summarize")
    async def summarize(
        self,
        input_text: str,
        model: LlmClient,
        caching_policy: CachingPolicy = CachingPolicy.USE_CACHING,
    ) -> AlertSummaryAiResponse | None:
        in_mem_cache_key = (input_text, model.id())
        if in_mem_cache_key in self._in_memory_cache:
            logger.info(f"In-memory cache hit for key: {in_mem_cache_key}")
            return self._in_memory_cache[in_mem_cache_key]
            # Note: Memory cache is per Lambda invocation - useful for batch processing

        logger.info(f"In-memory cache miss for key: {in_mem_cache_key}")

        # Handle CACHE_ONLY policy
        if caching_policy == CachingPolicy.CACHE_ONLY:
            result = await self.summarize_from_cache(input_text, model)
            if result:
                self._in_memory_cache[in_mem_cache_key] = result
            return result

        # Handle USE_CACHING policy
        if caching_policy == CachingPolicy.USE_CACHING:
            result = await self.summarize_from_cache(input_text, model)
            if result:
                self._in_memory_cache[in_mem_cache_key] = result
                return result

        # For NO_CACHE and USE_CACHING (when cache miss), proceed with LLM call
        try:
            hash_key = self._cache_service.hash_llm_key(input_text, model)

            response_data = AlertSummaryAiResponse(
                input_string_hash=hash_key,
                code_version_hash=CacheService.hash_category_key(),
                input=input_text,
                model=model.id(),
                response=await self._get_ai_response(input_text, model),
                generated_at=int(time.time()),
            )

            self._cache_service.save(response_data)
            self._in_memory_cache[in_mem_cache_key] = response_data

            return response_data

        except Exception as e:
            logger.error(f"Error calling LLM API: {str(e)}", exc_info=True)
            raise

    @tracer.start_as_current_span("summ.summarize_from_cache")
    async def summarize_from_cache(
        self, input_text: str, model: LlmClient
    ) -> AlertSummaryAiResponse | None:
        hash_key = self._cache_service.hash_llm_key(input_text, model)
        cache_content = self._cache_service.get(hash_key)
        if cache_content:
            logger.info(f"Cache hit for hash: {hash_key}")
            return cache_content
        else:
            logger.info(f"Cache miss for hash: {hash_key}")
            return None

    @tracer.start_as_current_span("summ.get_ai_response")
    async def _get_ai_response(
        self, input_text: str, model: LlmClient
    ) -> AlertSummaryContainer:
        trace.get_current_span().set_attribute(key="llm", value=model.id())
        cr = self._client_registry(model)

        if (
            "Sports Illustrated Stadium" in input_text
            and model.id() == PREFERRED_LLM.id()
        ):
            date_and_time_response = (
                await self._llm_service.get_red_bull_arena_date_time(cr, input_text)
            )
            date_and_time = date_and_time_response.response
            logger.info(f"Red Bull Arena date and time: {date_and_time}")
            redbull_arena_info_response = await self._llm_service.get_red_bull_arena_info(
                f"Sports Illustrated Stadium event at {date_and_time.date} at {date_and_time.time}"
            )
            redbull_arena_info = redbull_arena_info_response.response
            event_date = redbull_arena_info.event_date.replace(",", "")
            event_time = redbull_arena_info.event_time.replace(" ", "").lower()
            summary_text = f"At Sports Illustrated Stadium (formerly Red Bull Arena), there is a {redbull_arena_info.event_name} {redbull_arena_info.event_type} on {event_date} at {event_time}. Allow extra travel time to get to the stadium."
            logger.info(f"Red Bull Arena info: {redbull_arena_info}")
            affected_stations = AffectedStations(affected_stations=[PathStation.HAR])

            return AlertSummaryContainer(
                text=summary_text,
                is_delay=True,
                affected_stations=affected_stations,
                affected_lines=None,
                affected_area_cost=Decimal(0.0),
                summary_cost=aggregate_llm_costs(
                    [
                        date_and_time_response,
                        redbull_arena_info_response,
                    ]
                ).total_cost,
            )

        summary_response, affected_area_response = await asyncio.gather(
            self._llm_service.get_alert_summary(cr, input_text),
            self._llm_service.get_affected_area(input_text),
        )

        summary = summary_response.response
        (affected_stations, affected_lines) = affected_area_response.response
        summary_text_obj_response: LlmResponseWithCost[AlertSummary] | None = None

        if model.id() != FAST_LLM.id():
            single_area_text = None
            affected_lines_list = (
                affected_lines.affected_lines if affected_lines else []
            )
            affected_stations_list = (
                affected_stations.affected_stations if affected_stations else []
            )

            if len(affected_stations_list) == 1 and len(affected_lines_list) == 0:
                station_enum = affected_stations_list[0]
                station_description = PATH_STATION_DESCRIPTIONS.get(
                    station_enum, str(station_enum)
                )
                single_area_text = f"{station_description} station"
            elif len(affected_lines_list) == 1 and len(affected_stations_list) == 0:
                line_enum = affected_lines_list[0]
                line_description = PATH_LINE_DESCRIPTIONS.get(line_enum, str(line_enum))
                single_area_text = f"{line_description} line"

            if single_area_text:
                logger.info(
                    f"Single area text before: {single_area_text} \n Summary: {summary.alert_summary}"
                )
                summary_text_obj_response = (
                    await self._llm_service.remove_single_line_or_route_from_summary(
                        cr, summary.alert_summary, single_area_text
                    )
                )
                summary_text_obj = summary_text_obj_response.response
                logger.info(f"Single area text after: {summary_text_obj.alert_summary}")
                summary_text = summary_text_obj.alert_summary
            else:
                summary_text = summary.alert_summary
        else:
            summary_text = summary.alert_summary

        return AlertSummaryContainer(
            text=summary_text,
            is_delay=True,
            affected_lines=affected_lines,
            affected_stations=affected_stations,
            affected_area_cost=aggregate_llm_costs(
                [
                    affected_area_response,
                ]
            ).total_cost,
            summary_cost=aggregate_llm_costs(
                [
                    summary_response,
                    summary_text_obj_response,
                ]
            ).total_cost,
        )

    @staticmethod
    def _client_registry(model: LlmClient) -> ClientRegistry:
        return LlmService.create_client_registry(model)
