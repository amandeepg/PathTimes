import asyncio
import os
import time

from aws_lambda_powertools import Logger
from baml_py import ClientRegistry, Collector
from opentelemetry import trace

from baml_client import b
from baml_client.types import (
    AffectedRoutes,
    AffectedStations,
    AlertSummary,
    DateAndTime,
    PathLine,
    PathStation,
    RedBullArenaInfo,
)

from .cache import CacheService
from .llm_client_base import LlmClient
from .llm_clients import FAST_LLM, PREFERRED_LLM
from .models import AlertSummaryContainer, AlertSummaryAiResponse

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


class RateLimitedException(Exception):
    pass


class AlertSummarizer:
    def __init__(self):
        self._cache_service = CacheService()
        logger.info("Initialized AlertSummarizer")

    class __Token:
        def __init__(
            self, data: AlertSummaryAiResponse | None, input_text: str, model: LlmClient
        ):
            self.data = data
            self.input_text = input_text
            self.model = model

    @tracer.start_as_current_span("summ.check_for_cached")
    async def check_for_cached(
        self,
        input_text: str,
        model: LlmClient,
        skip_cache: bool = False,
    ) -> tuple[bool, str, __Token]:
        hash_key = self._cache_service.hash_llm_key(input_text, model)
        if not skip_cache and (
            response_data := await self.summarize_from_cache(input_text, model)
        ):
            return True, hash_key, self.__Token(response_data, input_text, model)

        return False, hash_key, self.__Token(None, input_text, model)

    @tracer.start_as_current_span("summ.summarize")
    async def summarize(
        self,
        token: __Token,
    ) -> AlertSummaryAiResponse:
        if token.data:
            return token.data

        try:
            hash_key = self._cache_service.hash_llm_key(token.input_text, token.model)

            response_data = AlertSummaryAiResponse(
                input_string_hash=hash_key,
                code_version_hash=CacheService.hash_category_key(),
                input=token.input_text,
                model=token.model.id(),
                response=await self._get_ai_response(token.input_text, token.model),
                generated_at=int(time.time()),
            )

            self._cache_service.save(response_data)

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
            date_and_time = await self._get_red_bull_arena_date_time(cr, input_text)
            logger.info(f"Red Bull Arena date and time: {date_and_time}")
            redbull_arena_info = await self._get_red_bull_arena_info(
                f"Sports Illustrated Stadium event at {date_and_time.date} at {date_and_time.time}"
            )
            event_date = redbull_arena_info.event_date.replace(",", "")
            event_time = redbull_arena_info.event_time.replace(" ", "").lower()
            summary_text = f"At Sports Illustrated Stadium (formerly Red Bull Arena), there is a {redbull_arena_info.event_name} {redbull_arena_info.event_type} on {event_date} at {event_time}. Allow extra travel time to get to the stadium."
            logger.info(f"Red Bull Arena info: {redbull_arena_info}")
            affected_area = AffectedStations(affected_stations=[PathStation.HAR])

            return AlertSummaryContainer(
                text=summary_text,
                is_delay=True,
                affected_area=affected_area,
            )

        summary, affected_area = await asyncio.gather(
            self._get_alert_summary(cr, input_text),
            self._get_affected_area(cr, input_text),
        )

        if model.id() != FAST_LLM.id():
            single_area_text = None
            if isinstance(affected_area, AffectedStations):
                if len(affected_area.affected_stations) == 1:
                    station_enum = affected_area.affected_stations[0]
                    station_description = PATH_STATION_DESCRIPTIONS.get(
                        station_enum, str(station_enum)
                    )
                    single_area_text = f"{station_description} station"
            elif isinstance(affected_area, AffectedRoutes):
                if len(affected_area.affected_routes) == 1:
                    route_enum = affected_area.affected_routes[0]
                    route_description = PATH_LINE_DESCRIPTIONS.get(
                        route_enum, str(route_enum)
                    )
                    single_area_text = f"{route_description} route"

            if single_area_text:
                logger.info(
                    f"Single area text before: {single_area_text} \n Summary: {summary.alert_summary}"
                )
                summary_text_obj = await self._remove_single_line_or_route_from_summary(
                    cr, summary.alert_summary, single_area_text
                )
                logger.info(f"Single area text after: {summary_text_obj.alert_summary}")
                summary_text = summary_text_obj.alert_summary
            else:
                summary_text = summary.alert_summary
        else:
            summary_text = summary.alert_summary

        return AlertSummaryContainer(
            text=summary_text,
            is_delay=True,
            affected_area=affected_area,
        )

    @tracer.start_as_current_span("summ.get_affected_area")
    async def _get_affected_area(
        self, cr: ClientRegistry, input_text: str
    ) -> AffectedStations | AffectedRoutes | None:
        return await b.GetAffectedArea(input_text, baml_options={"client_registry": cr})

    @tracer.start_as_current_span("summ.remove_single_line_or_route_from_summary")
    async def _remove_single_line_or_route_from_summary(
        self,
        cr: ClientRegistry,
        input_text: str,
        single_area: str,
    ) -> AlertSummary:
        return await b.RemoveSingleLineOrRouteFromSummary(
            input_text, single_area, baml_options={"client_registry": cr}
        )

    @tracer.start_as_current_span("summ.get_alert_summary")
    async def _get_alert_summary(
        self, cr: ClientRegistry, input_text: str
    ) -> AlertSummary:
        collector = Collector()
        summary = await b.GetAlertSummary(
            input_text, baml_options={"client_registry": cr, "collector": collector}
        )

        total_cost = 0
        for call in collector.last.calls:  # pyright: ignore[reportOptionalMemberAccess]
            try:
                resp = call.http_response
                if not resp:
                    continue
                cost_str = resp.body.json()["usage"]["cost"]
                if not cost_str:
                    continue
                total_cost += float(cost_str)
            except (AttributeError, KeyError, TypeError):
                # Handle missing attributes, keys, or type errors gracefully
                continue

        logger.info(f"LLM call cost for GetAlertSummary: {total_cost}")

        return summary

    @tracer.start_as_current_span("summ.get_red_bull_arena_date_time")
    async def _get_red_bull_arena_date_time(
        self, cr: ClientRegistry, input_text: str
    ) -> DateAndTime:
        return await b.GetRedBullsArenaDateTime(
            input_text, baml_options={"client_registry": cr}
        )

    @tracer.start_as_current_span("summ.get_red_bull_arena_info")
    async def _get_red_bull_arena_info(self, input_text: str) -> RedBullArenaInfo:
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
        return await b.GetRedBullsArenaText(
            input_text, baml_options={"client_registry": cr}
        )

    @staticmethod
    def _client_registry(model: LlmClient) -> ClientRegistry:
        cr = ClientRegistry()
        model.add_to_registry(cr)
        cr.set_primary(model.id())
        return cr
