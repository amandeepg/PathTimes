from dataclasses import dataclass
from decimal import Decimal
from time import perf_counter
from typing import cast

import dspy
from typing_extensions import override

from ..types import LlmType
from src.models import AffectedLines, AffectedStations
from ..dspy_utils import (
    UsageResult,
    ModuleCallResult,
    usage_from_prediction,
    lm_for_type,
)
from .base import BaseResponse, LoggerLike

ELEVATOR_AREAS_TEMPLATE = BaseResponse.load_prompt("elevator_areas_prompt.txt")


class ElevatorAffectedAreasSignature(dspy.Signature):
    """{ELEVATOR_AREAS_TEMPLATE}"""

    alert: str = dspy.InputField(desc="Elevator-specific alert text.")
    affected_stations: AffectedStations | None = dspy.OutputField(
        desc="Structured AffectedStations result for elevator alerts."
    )


class ElevatorAffectedAreasModule(dspy.Module):
    def __init__(self) -> None:
        super().__init__()
        self._lm: dspy.LM = lm_for_type(LlmType.FAST)
        self._predict: dspy.Predict = dspy.Predict(ElevatorAffectedAreasSignature)

    @override
    def forward(self, alert: str) -> ModuleCallResult:
        with dspy.context(lm=self._lm):
            prediction = self._predict(alert=alert)
        model_name = self._lm.model
        stations: AffectedStations | None = getattr(
            prediction, "affected_stations", None
        )

        structured_prediction = dspy.Prediction(
            alert=alert, affected_lines=None, affected_stations=stations
        )
        generation, pricing = usage_from_prediction(
            structured_prediction,
            llm_type=LlmType.FAST,
            model_name=model_name,
        )
        return ModuleCallResult(
            prediction=structured_prediction,
            generation=generation,
            model_name=model_name,
            pricing=pricing,
        )


@dataclass(frozen=True)
class ElevatorAffectedAreasResponse(BaseResponse):
    lines: AffectedLines | None
    stations: AffectedStations | None
    usage: UsageResult
    llm_type: LlmType
    pricing: tuple[Decimal, Decimal]

    @classmethod
    @override
    def from_alert(
        cls, *args: object, **kwargs: object
    ) -> "ElevatorAffectedAreasResponse":
        alert = cast(str, kwargs.get("alert") or (args[0] if args else ""))
        logger = cast(
            LoggerLike | None,
            kwargs.get("logger") or (args[1] if len(args) > 1 else None),
        )
        module = ElevatorAffectedAreasModule()
        start = perf_counter()
        call = cast(ModuleCallResult, module(alert=alert))
        result = call.generation
        cls.log_llm_call(
            logger,
            "affected_areas_elevator",
            "",
            result,
            start,
            call.model_name,
        )

        lines = cast(AffectedLines | None, call.prediction.affected_lines)
        stations = cast(AffectedStations | None, call.prediction.affected_stations)

        return cls(
            lines=lines,
            stations=stations,
            usage=result,
            llm_type=LlmType.FAST,
            pricing=call.pricing,
        )
