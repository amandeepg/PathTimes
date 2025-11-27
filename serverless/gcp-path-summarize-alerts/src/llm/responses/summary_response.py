from dataclasses import dataclass
from decimal import Decimal
from time import perf_counter

from ..types import LlmType
from ..clients.llm_client import GenerationResult
from ..clients.clients import gemini_flash_client, gpt_5_1_client
from .base import BaseResponse, LoggerLike
from .preprocess_response import PreprocessResponse
from .remove_single_area_response import RemoveSingleAreaResponse

SUMMARY_TEMPLATE = BaseResponse.load_prompt("summary_prompt.txt")


@dataclass(frozen=True)
class SummaryResponse(BaseResponse):
    text: str
    usage: GenerationResult
    llm_type: LlmType
    pricing: tuple[Decimal, Decimal]
    extra_responses: tuple[BaseResponse, ...] = ()

    @classmethod
    def from_alert(  # type: ignore[override]  # pyright: ignore[reportIncompatibleMethodOverride,reportImplicitOverride]
        cls,
        alert: str,
        llm_type: LlmType,
        logger: LoggerLike | None = None,
        single_area: str | None = None,
        *args: object,
        **kwargs: object,
    ) -> "SummaryResponse":
        _ = args
        _ = kwargs
        preprocessed = PreprocessResponse.from_alert(alert, logger=logger)

        if llm_type is LlmType.FAST:
            client = gemini_flash_client()
        else:
            client = gpt_5_1_client()
        model_name = client.model_name
        prompt = SUMMARY_TEMPLATE.format(alert=preprocessed.text)
        start = perf_counter()
        result = client.generate_text_with_usage(prompt)
        cls.log_llm_call(logger, "summary", prompt, result, start, model_name)

        final_text = result.text
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
            pricing=client.pricing,
            extra_responses=tuple(extras),
        )

    def total_cost(self) -> Decimal:
        return self.combine_costs((self, *self.extra_responses))
