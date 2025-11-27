import logging
from collections.abc import Iterable
from decimal import Decimal
from pathlib import Path
from time import perf_counter
from typing import Final, cast

from ..types import LlmType
from ..clients.llm_client import GenerationResult
from ..clients.clients import gemini_flash_client, gpt_5_1_client

LoggerLike = logging.Logger | logging.LoggerAdapter[logging.Logger]


class BaseResponse:
    _PROMPTS_DIR: Final[Path] = Path(__file__).resolve().parent / "prompts"

    @classmethod
    def from_alert(  # type: ignore[override, unused-argument]
        cls, *args: object, **kwargs: object
    ) -> "BaseResponse":
        _ = args
        _ = kwargs
        raise NotImplementedError

    @classmethod
    def model_name_for_type(cls, llm_type: LlmType) -> str:
        if llm_type is LlmType.FAST:
            return gemini_flash_client().model_name
        return gpt_5_1_client().model_name

    def cost(self) -> Decimal:
        usage = cast(GenerationResult, getattr(self, "usage"))
        pricing = cast(tuple[Decimal, Decimal], getattr(self, "pricing"))
        input_cost_per_1m, output_cost_per_1m = pricing
        prompt_tokens = Decimal(usage.prompt_tokens)
        output_tokens = Decimal(usage.output_tokens)
        cost = (prompt_tokens / Decimal(1_000_000)) * input_cost_per_1m
        cost += (output_tokens / Decimal(1_000_000)) * output_cost_per_1m
        return cost.quantize(Decimal("0.000001"))

    @staticmethod
    def combine_costs(
        responses: Iterable["BaseResponse"],
    ) -> Decimal:
        total = Decimal("0")
        for resp in responses:
            total += resp.cost()
        return total

    @classmethod
    def load_prompt(cls, filename: str) -> str:
        return (cls._PROMPTS_DIR / filename).read_text(encoding="utf-8")

    @staticmethod
    def strip_code_fence(text: str) -> str:
        stripped = text.strip()
        if stripped.startswith("```") and stripped.endswith("```"):
            inner = stripped[3:-3].strip()
            if inner.lower().startswith("json"):
                inner = inner[4:].strip()
            return inner
        return text

    @staticmethod
    def log_llm_call(
        logger: LoggerLike | None,
        phase: str,
        prompt: str,
        result: GenerationResult,
        started_at: float,
        model_name: str,
    ) -> None:
        if not logger:
            return
        duration = perf_counter() - started_at
        logger.info(
            "llm_call %s",
            {
                "phase": phase,
                "model": model_name,
                "duration_s": round(duration, 6),
                "prompt_tokens": result.prompt_tokens,
                "output_tokens": result.output_tokens,
                "prompt": prompt,
                "response_text": result.text,
            },
        )
