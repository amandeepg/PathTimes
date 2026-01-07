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

PREPROCESS_TEMPLATE = BaseResponse.load_prompt("preprocess_prompt.txt")


class PreprocessSignature(dspy.Signature):
    f"""{PREPROCESS_TEMPLATE}"""

    alert: str = dspy.InputField(desc="Raw alert text to normalize.")
    cleaned_text: str = dspy.OutputField(desc="Normalized, compact alert.")


class PreprocessModule(dspy.Module):
    """DSPy module encapsulating the preprocess prompt and call."""

    def __init__(self) -> None:
        super().__init__()
        self._lm: dspy.LM = lm_for_type(LlmType.FAST)
        self._predict: dspy.Predict = dspy.Predict(PreprocessSignature)

    @override
    def forward(self, alert: str) -> ModuleCallResult:
        with dspy.context(lm=self._lm):
            prediction = self._predict(alert=alert)
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
class PreprocessResponse(BaseResponse):
    text: str
    usage: UsageResult
    pricing: tuple[Decimal, Decimal]
    llm_type: LlmType = LlmType.FAST

    @classmethod
    @override
    def from_alert(cls, *args: object, **kwargs: object) -> "PreprocessResponse":
        alert = cast(str, kwargs.get("alert") or (args[0] if args else ""))
        logger = cast(
            LoggerLike | None,
            kwargs.get("logger") or (args[1] if len(args) > 1 else None),
        )
        module = PreprocessModule()
        start = perf_counter()
        call = cast(ModuleCallResult, module(alert=alert))
        result = call.generation
        cls.log_llm_call(logger, "preprocess", "", result, start, call.model_name)
        return cls(
            text=cast(str, call.prediction.cleaned_text),
            usage=result,
            llm_type=LlmType.FAST,
            pricing=call.pricing,
        )
