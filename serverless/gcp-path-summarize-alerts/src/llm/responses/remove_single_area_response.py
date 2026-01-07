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

REMOVE_SINGLE_AREA_TEMPLATE = BaseResponse.load_prompt("remove_single_area_prompt.txt")


class RemoveSingleAreaSignature(dspy.Signature):
    f"""{REMOVE_SINGLE_AREA_TEMPLATE}"""

    text: str = dspy.InputField(desc="Current summary text.")
    single_area: str = dspy.InputField(desc="Area/line/station to remove if alone.")
    updated_text: str = dspy.OutputField(
        desc="Summary with area removed when applicable."
    )


class RemoveSingleAreaModule(dspy.Module):
    def __init__(self) -> None:
        super().__init__()
        self._lm: dspy.LM = lm_for_type(LlmType.FAST)
        self._predict: dspy.Predict = dspy.Predict(RemoveSingleAreaSignature)

    @override
    def forward(self, text: str, single_area: str) -> ModuleCallResult:
        with dspy.context(lm=self._lm):
            prediction = self._predict(text=text, single_area=single_area)
        model_name = self._lm.model
        generation, pricing = usage_from_prediction(
            prediction,
            llm_type=LlmType.FAST,
            model_name=model_name,
        )
        return ModuleCallResult(
            prediction=prediction,
            generation=generation,
            model_name=model_name,
            pricing=pricing,
        )


@dataclass(frozen=True)
class RemoveSingleAreaResponse(BaseResponse):
    text: str
    usage: UsageResult
    pricing: tuple[Decimal, Decimal]
    llm_type: LlmType = LlmType.FAST

    @classmethod
    def from_text(
        cls, text: str, single_area: str, logger: LoggerLike | None = None
    ) -> "RemoveSingleAreaResponse":
        module = RemoveSingleAreaModule()
        start = perf_counter()
        call = cast(ModuleCallResult, module(text=text, single_area=single_area))
        result = call.generation
        cls.log_llm_call(
            logger, "remove_single_area", "", result, start, call.model_name
        )
        return cls(
            text=cast(str, call.prediction.updated_text),
            usage=result,
            llm_type=LlmType.FAST,
            pricing=call.pricing,
        )
