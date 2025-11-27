from decimal import Decimal
from typing import Callable, Final, cast

import vertexai
from vertexai.generative_models import GenerationConfig, GenerativeModel

from ...settings import LOCATION, PROJECT_ID
from .llm_client import GenerationResult


class VertexClient:
    """Lightweight wrapper around a Vertex AI generative model."""

    def __init__(
        self,
        model_name: str,
        pricing: tuple[Decimal, Decimal],
        project_id: str = PROJECT_ID,
        location: str = LOCATION,
        thinking_budget_tokens: int | None = None,
    ) -> None:
        self._model_name: Final[str] = model_name
        self._pricing: Final[tuple[Decimal, Decimal]] = pricing
        self._project_id: Final[str] = project_id
        self._location: Final[str] = location
        self._thinking_budget_tokens: Final[int | None] = thinking_budget_tokens
        vertexai.init(project=self._project_id, location=self._location)
        self._model: GenerativeModel = GenerativeModel(self._model_name)

    @property
    def model_name(self) -> str:
        return self._model_name

    @property
    def pricing(self) -> tuple[Decimal, Decimal]:
        return self._pricing

    def generate_text_with_usage(self, prompt: str) -> GenerationResult:
        response = self._model.generate_content(
            prompt,
            generation_config=self._build_generation_config(),
        )
        text = self._extract_text(response)
        prompt_tokens, total_tokens = self._extract_usage(response)
        return GenerationResult(
            text=text, prompt_tokens=prompt_tokens, total_tokens=total_tokens
        )

    def generate_json_with_usage(self, prompt: str) -> GenerationResult:
        generate_content = cast(Callable[..., object], self._model.generate_content)
        response = generate_content(
            prompt,
            generation_config=self._build_generation_config(
                response_mime_type="application/json"
            ),
        )
        text = self._extract_text(response)
        prompt_tokens, total_tokens = self._extract_usage(response)
        return GenerationResult(
            text=text, prompt_tokens=prompt_tokens, total_tokens=total_tokens
        )

    def _build_generation_config(
        self, *, response_mime_type: str | None = None
    ) -> GenerationConfig:
        config: dict[str, object] = {"temperature": 0.0}
        if response_mime_type:
            config["response_mime_type"] = response_mime_type
        if self._thinking_budget_tokens is not None:
            config["thinking_config"] = {
                # Using snake_case accepted by GenerationConfig.from_dict.
                "thinking_budget": self._thinking_budget_tokens
            }
        return GenerationConfig.from_dict(config)

    @staticmethod
    def _extract_text(response: object) -> str:
        text = cast(str | None, getattr(response, "text", None)) or ""
        if text:
            return text.strip()

        try:
            candidates_attr = getattr(response, "candidates", [])
            candidates = cast(list[object], candidates_attr)
            if candidates:
                first: object = candidates[0]
                content_attr = getattr(first, "content", None)
                content = cast(object | None, content_attr)
                parts_attr = getattr(content, "parts", None) if content else None
                parts_obj = cast(object | None, parts_attr)
                if isinstance(parts_obj, list) and parts_obj:
                    parts_list = cast(list[object], parts_obj)
                    first_part: object = parts_list[0]
                    part_text_attr = getattr(first_part, "text", None)
                    part_text = cast(str | None, part_text_attr)
                    if part_text:
                        return part_text.strip()
        except Exception:
            return ""
        return ""

    @staticmethod
    def _extract_usage(response: object) -> tuple[int, int]:
        usage_obj = cast(object | None, getattr(response, "usage_metadata", None))
        prompt_tokens_attr = (
            getattr(usage_obj, "prompt_token_count", 0) if usage_obj else 0
        )
        total_tokens_attr = (
            getattr(usage_obj, "total_token_count", 0) if usage_obj else 0
        )
        prompt_tokens = (
            int(prompt_tokens_attr) if isinstance(prompt_tokens_attr, int) else 0
        )
        total_tokens = (
            int(total_tokens_attr) if isinstance(total_tokens_attr, int) else 0
        )
        return prompt_tokens, total_tokens
