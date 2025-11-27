from dataclasses import dataclass
from decimal import Decimal
from time import perf_counter

from ..types import LlmType
from ..clients.llm_client import GenerationResult
from ..clients.clients import gemini_flash_client
from .base import BaseResponse, LoggerLike

REMOVE_SINGLE_AREA_TEMPLATE = BaseResponse.load_prompt("remove_single_area_prompt.txt")


@dataclass(frozen=True)
class RemoveSingleAreaResponse(BaseResponse):
    text: str
    usage: GenerationResult
    pricing: tuple[Decimal, Decimal]
    llm_type: LlmType = LlmType.FAST

    @classmethod
    def from_text(
        cls, text: str, single_area: str, logger: LoggerLike | None = None
    ) -> "RemoveSingleAreaResponse":
        client = gemini_flash_client()
        model_name = client.model_name
        prompt = REMOVE_SINGLE_AREA_TEMPLATE.format(alert=text, single_area=single_area)
        start = perf_counter()
        result = client.generate_text_with_usage(prompt)
        cls.log_llm_call(
            logger, "remove_single_area", prompt, result, start, model_name
        )
        return cls(
            text=result.text,
            usage=result,
            llm_type=LlmType.FAST,
            pricing=client.pricing,
        )
