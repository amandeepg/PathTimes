from dataclasses import dataclass
from decimal import Decimal
from typing import Protocol


@dataclass(frozen=True)
class GenerationResult:
    text: str
    prompt_tokens: int
    total_tokens: int

    @property
    def output_tokens(self) -> int:
        if self.total_tokens < self.prompt_tokens:
            return 0
        return self.total_tokens - self.prompt_tokens


class LlmClient(Protocol):
    """Minimal interface for LLM client operations we rely on."""

    @property
    def model_name(self) -> str: ...

    @property
    def pricing(self) -> tuple[Decimal, Decimal]: ...

    def generate_text_with_usage(self, prompt: str) -> GenerationResult: ...

    def generate_json_with_usage(self, prompt: str) -> GenerationResult: ...
