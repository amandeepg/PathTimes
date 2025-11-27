from dataclasses import dataclass
from decimal import Decimal
from typing import Final, Literal
from typing_extensions import override

from openai import OpenAI
from openai.types.chat import ChatCompletion

from .llm_client import GenerationResult, LlmClient


@dataclass(frozen=True)
class OpenAIClient(LlmClient):
    """Simple LLM client using the official OpenAI Python library."""

    _model_name: Final[str]
    _client: Final[OpenAI]
    _pricing: Final[tuple[Decimal, Decimal]]
    _reasoning_effort: Final[Literal["none", "minimal", "low", "medium", "high"] | None]

    def __init__(
        self,
        model_name: str,
        pricing: tuple[Decimal, Decimal],
        api_key: str | None = None,
        reasoning_effort: Literal["none", "minimal", "low", "medium", "high"] | None = None,
    ) -> None:
        object.__setattr__(self, "_model_name", model_name)
        object.__setattr__(self, "_pricing", pricing)
        object.__setattr__(self, "_client", OpenAI(api_key=api_key))
        object.__setattr__(self, "_reasoning_effort", reasoning_effort)

    @property
    @override
    def model_name(self) -> str:
        return self._model_name

    @property
    @override
    def pricing(self) -> tuple[Decimal, Decimal]:
        return self._pricing

    def _run_completion(self, prompt: str, *, json_mode: bool) -> ChatCompletion:
        if self._reasoning_effort is None:
            return self._client.chat.completions.create(  # type: ignore[arg-type]
                model=self._model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.0,
                response_format={"type": "json_object"} if json_mode else {"type": "text"},
            )
        return self._client.chat.completions.create(  # type: ignore[arg-type]
            model=self._model_name,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.0,
            response_format={"type": "json_object"} if json_mode else {"type": "text"},
            reasoning_effort=self._reasoning_effort,
        )

    @override
    def generate_text_with_usage(self, prompt: str) -> GenerationResult:
        response = self._run_completion(prompt, json_mode=False)
        text = response.choices[0].message.content or ""
        usage = response.usage or None
        prompt_tokens = int(getattr(usage, "prompt_tokens", 0) or 0)
        total_tokens = int(getattr(usage, "total_tokens", 0) or 0)
        return GenerationResult(
            text=text.strip(), prompt_tokens=prompt_tokens, total_tokens=total_tokens
        )

    @override
    def generate_json_with_usage(self, prompt: str) -> GenerationResult:
        response = self._run_completion(prompt, json_mode=True)
        text = response.choices[0].message.content or ""
        usage = response.usage or None
        prompt_tokens = int(getattr(usage, "prompt_tokens", 0) or 0)
        total_tokens = int(getattr(usage, "total_tokens", 0) or 0)
        return GenerationResult(
            text=text.strip(), prompt_tokens=prompt_tokens, total_tokens=total_tokens
        )
