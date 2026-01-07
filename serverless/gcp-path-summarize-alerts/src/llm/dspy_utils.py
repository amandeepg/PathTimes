from __future__ import annotations

import os
from dataclasses import dataclass
from decimal import Decimal
from functools import cache
from typing import Final, cast

import dspy

from .types import LlmType

PROJECT_ID = os.getenv("PROJECT_ID") or os.getenv("GOOGLE_CLOUD_PROJECT") or "local-dev"
LOCATION = os.getenv("VERTEX_LOCATION") or os.getenv("LOCATION") or "us-central1"
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

FAST_MODEL_NAME: Final = "vertex_ai/gemini-2.5-flash"
SLOW_MODEL_NAME: Final = "openai/gpt-5.2"
FAST_PRICING: Final = (Decimal("0.30"), Decimal("2.50"))
SLOW_PRICING: Final = (Decimal("1.75"), Decimal("14.00"))

_configured = False


@dataclass(frozen=True)
class ModuleCallResult:
    prediction: dspy.Prediction
    generation: "UsageResult"
    model_name: str
    pricing: tuple[Decimal, Decimal]


@dataclass(frozen=True)
class UsageResult:
    prompt_tokens: int
    total_tokens: int

    @property
    def output_tokens(self) -> int:
        if self.total_tokens < self.prompt_tokens:
            return 0
        return self.total_tokens - self.prompt_tokens


def _ensure_provider_env() -> None:
    _ = os.environ.setdefault("VERTEXAI_PROJECT", PROJECT_ID)
    _ = os.environ.setdefault("VERTEXAI_LOCATION", LOCATION)
    if OPENAI_API_KEY:
        _ = os.environ.setdefault("OPENAI_API_KEY", OPENAI_API_KEY)


def _configure_if_needed(default_lm: dspy.LM) -> None:
    global _configured
    if _configured:
        return
    dspy.configure(lm=default_lm, track_usage=True)
    _configured = True


@cache
def _base_fast_lm() -> dspy.LM:
    _ensure_provider_env()
    return dspy.LM(
        FAST_MODEL_NAME,
        model_type="chat",
        temperature=0.0,
        cache=False,
        vertex_project=PROJECT_ID,
        vertex_location=LOCATION,
    )


@cache
def _base_slow_lm() -> dspy.LM:
    _ensure_provider_env()
    return dspy.LM(
        SLOW_MODEL_NAME,
        model_type="chat",
        temperature=1.0 if "gpt-5" in SLOW_MODEL_NAME else 0.0,
        max_tokens=50000 if "gpt-5" in SLOW_MODEL_NAME else None,
        cache=False,
        reasoning_effort="high",
        api_key=OPENAI_API_KEY,
    )


def lm_for_type(llm_type: LlmType) -> dspy.LM:
    base = _base_fast_lm() if llm_type is LlmType.FAST else _base_slow_lm()
    _configure_if_needed(base)
    return base.copy(cache=False)


def model_name_for_type(llm_type: LlmType) -> str:
    return FAST_MODEL_NAME if llm_type is LlmType.FAST else SLOW_MODEL_NAME


def pricing_for_type(llm_type: LlmType) -> tuple[Decimal, Decimal]:
    return FAST_PRICING if llm_type is LlmType.FAST else SLOW_PRICING


def usage_from_prediction(
    prediction: dspy.Prediction,
    llm_type: LlmType,
    model_name: str,
) -> tuple[UsageResult, tuple[Decimal, Decimal]]:
    usage_fn = getattr(prediction, "get_lm_usage", None)
    prompt_tokens = 0
    total_tokens = 0
    if callable(usage_fn):
        usage_dict = cast(dict[str, dict[str, object]], usage_fn() or {})
        model_usage = usage_dict.get(model_name, {}) or {}
        prompt_tokens = int(cast(int | None, model_usage.get("prompt_tokens")) or 0)
        completion_tokens = int(
            cast(int | None, model_usage.get("completion_tokens")) or 0
        )
        total_tokens = int(
            cast(int | None, model_usage.get("total_tokens"))
            or (prompt_tokens + completion_tokens)
        )
        if total_tokens < prompt_tokens + completion_tokens:
            total_tokens = prompt_tokens + completion_tokens
    return (
        UsageResult(prompt_tokens=prompt_tokens, total_tokens=total_tokens),
        pricing_for_type(llm_type),
    )
