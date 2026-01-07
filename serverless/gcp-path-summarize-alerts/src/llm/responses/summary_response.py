from dataclasses import dataclass
from decimal import Decimal
from time import perf_counter
from typing import cast

import dspy
from typing_extensions import override

from ..types import LlmType
from ..dspy_utils import (
    UsageResult,
    ModuleCallResult,
    usage_from_prediction,
    lm_for_type,
)
from .base import BaseResponse, LoggerLike
from .preprocess_response import PreprocessResponse
from .remove_single_area_response import RemoveSingleAreaResponse

SUMMARY_TEMPLATE = BaseResponse.load_prompt("summary_prompt.txt")


class SummarySignature(dspy.Signature):
    f"""{SUMMARY_TEMPLATE}"""

    alert: str = dspy.InputField(desc="Normalized alert text to summarize.")
    summary: str = dspy.OutputField(desc="Concise user-facing summary.")


class SummaryModule(dspy.Module):
    def __init__(self, llm_type: LlmType) -> None:
        super().__init__()
        self._llm_type: LlmType = llm_type
        self._lm: dspy.LM = lm_for_type(llm_type)
        self._predict: dspy.Predict = dspy.Predict(SummarySignature)

    @override
    def forward(self, alert: str) -> ModuleCallResult:
        with dspy.context(lm=self._lm):
            prediction = self._predict(alert=alert)
        model_name = self._lm.model
        generation, pricing = usage_from_prediction(
            prediction,
            llm_type=self._llm_type,
            model_name=model_name,
        )
        return ModuleCallResult(
            prediction=prediction,
            generation=generation,
            model_name=model_name,
            pricing=pricing,
        )


@dataclass(frozen=True)
class SummaryResponse(BaseResponse):
    text: str
    usage: UsageResult
    llm_type: LlmType
    pricing: tuple[Decimal, Decimal]
    extra_responses: tuple[BaseResponse, ...] = ()

    @classmethod
    @override
    def from_alert(cls, *args: object, **kwargs: object) -> "SummaryResponse":
        alert = cast(str, kwargs.get("alert") or (args[0] if args else ""))
        llm_type = cast(
            LlmType,
            kwargs.get("llm_type") or (args[1] if len(args) > 1 else LlmType.FAST),
        )
        logger = cast(
            LoggerLike | None,
            kwargs.get("logger") or (args[2] if len(args) > 2 else None),
        )
        single_area = cast(
            str | None,
            kwargs.get("single_area") or (args[3] if len(args) > 3 else None),
        )

        preprocessed = PreprocessResponse.from_alert(alert=alert, logger=logger)

        module = SummaryModule(llm_type=llm_type)
        start = perf_counter()
        call = cast(ModuleCallResult, module(alert=preprocessed.text))
        result = call.generation
        cls.log_llm_call(logger, "summary", "", result, start, call.model_name)

        final_text = cast(str, call.prediction.summary)
        extras: list[BaseResponse] = [preprocessed]

        if single_area:
            removal = RemoveSingleAreaResponse.from_text(
                final_text, single_area, logger=logger
            )
            final_text = removal.text
            extras.append(removal)

        return cls(
            text=final_text,
            usage=result,
            llm_type=llm_type,
            pricing=call.pricing,
            extra_responses=tuple(extras),
        )

    def total_cost(self) -> Decimal:
        return self.combine_costs((self, *self.extra_responses))
