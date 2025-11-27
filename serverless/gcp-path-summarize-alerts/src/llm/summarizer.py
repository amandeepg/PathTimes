from concurrent.futures import Future, ThreadPoolExecutor
import logging
from time import perf_counter
from typing import Final, TypeAlias

from .responses import AffectedAreasResponse, SummaryResponse
from .types import LlmType
from ..models import AlertSummaryContainer

LoggerLike: TypeAlias = logging.Logger | logging.LoggerAdapter[logging.Logger]


class LlmSummarizer:
    """Abstraction over LLM summarization behavior using parallel model calls."""

    def __init__(
        self,
        speed: LlmType,
    ) -> None:
        self._speed: Final[LlmType] = speed
        self._model_name: Final[str] = SummaryResponse.model_name_for_type(speed)

    def summarize(
        self, text: str, *, logger: LoggerLike | None = None
    ) -> AlertSummaryContainer:
        start = perf_counter()
        with ThreadPoolExecutor(max_workers=3) as executor:
            areas_future = executor.submit(self._extract_areas, text, logger)
            summary_future = executor.submit(
                self._generate_summary, text, areas_future, logger
            )

            summary_result = summary_future.result()
            areas_result = areas_future.result()

        summary_cost = summary_result.total_cost()
        affected_area_cost = areas_result.cost()

        container = AlertSummaryContainer(
            text=summary_result.text,
            affected_lines=areas_result.lines,
            affected_stations=areas_result.stations,
            summary_cost=summary_cost,
            affected_area_cost=affected_area_cost,
        )
        if logger:
            duration = perf_counter() - start
            logger.info(
                "summarize_complete %s",
                {
                    "duration_s": round(duration, 6),
                    "summary_cost": str(summary_cost),
                    "affected_area_cost": str(affected_area_cost),
                    "model": self._model_name,
                },
            )
        return container

    def _generate_summary(
        self,
        text: str,
        areas_future: Future[AffectedAreasResponse],
        logger: LoggerLike | None,
    ) -> SummaryResponse:
        areas_result = areas_future.result()
        single_area = self._single_area_value(areas_result)
        return SummaryResponse.from_alert(
            text, self._speed, logger, single_area=single_area
        )

    def _extract_areas(
        self, text: str, logger: LoggerLike | None
    ) -> AffectedAreasResponse:
        return AffectedAreasResponse.from_alert(
            text,
            speed=self._speed,
            logger=logger,
        )

    @staticmethod
    def _single_area_value(areas: AffectedAreasResponse) -> str | None:
        lines = areas.lines.affected_lines if areas.lines else []
        stations = areas.stations.affected_stations if areas.stations else []
        if len(lines) == 1 and not stations:
            return lines[0].value
        if len(stations) == 1 and not lines:
            return stations[0].value
        return None
