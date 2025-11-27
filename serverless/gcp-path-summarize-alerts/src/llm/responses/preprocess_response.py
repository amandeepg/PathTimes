from dataclasses import dataclass
from decimal import Decimal
from time import perf_counter

from ..types import LlmType
from ..clients.llm_client import GenerationResult
from ..clients.clients import gemini_flash_client
from .base import BaseResponse, LoggerLike

PREPROCESS_TEMPLATE = BaseResponse.load_prompt("preprocess_prompt.txt")


@dataclass(frozen=True)
class PreprocessResponse(BaseResponse):
    text: str
    usage: GenerationResult
    pricing: tuple[Decimal, Decimal]
    llm_type: LlmType = LlmType.FAST

    @classmethod
    def from_alert(  # type: ignore[override]  # pyright: ignore[reportIncompatibleMethodOverride,reportImplicitOverride]
        cls,
        alert: str,
        logger: LoggerLike | None = None,
        *args: object,
        **kwargs: object,
    ) -> "PreprocessResponse":
        _ = args
        _ = kwargs
        client = gemini_flash_client()
        model_name = client.model_name
        prompt = PREPROCESS_TEMPLATE.format(alert=alert)
        start = perf_counter()
        result = client.generate_text_with_usage(prompt)
        cls.log_llm_call(logger, "preprocess", prompt, result, start, model_name)
        return cls(
            text=result.text,
            usage=result,
            llm_type=LlmType.FAST,
            pricing=client.pricing,
        )
