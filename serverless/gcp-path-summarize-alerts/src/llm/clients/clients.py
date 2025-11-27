from decimal import Decimal
from typing import Final

from .llm_client import LlmClient
from .openai_client import OpenAIClient
from .vertex_client import VertexClient
from ...settings import OPENAI_API_KEY

_GEMINI_FLASH_MODEL_NAME: Final[str] = "gemini-2.5-flash"
_GEMINI_PRO_MODEL_NAME: Final[str] = "gemini-2.5-pro"
_GPT_5_1_MODEL_NAME: Final[str] = "gpt-5.1"
_GEMINI_FLASH_THINKING_BUDGET = 0
_GEMINI_PRO_THINKING_BUDGET = 32768

_gemini_flash_client: Final[LlmClient] = VertexClient(
    model_name=_GEMINI_FLASH_MODEL_NAME,
    pricing=(Decimal("0.30"), Decimal("2.50")),
    thinking_budget_tokens=_GEMINI_FLASH_THINKING_BUDGET,
)
_gemini_pro_client: Final[LlmClient] = VertexClient(
    model_name=_GEMINI_PRO_MODEL_NAME,
    pricing=(Decimal("1.25"), Decimal("10.00")),
    thinking_budget_tokens=_GEMINI_PRO_THINKING_BUDGET,
)
_gpt_5_1_client: Final[LlmClient] = OpenAIClient(
    model_name=_GPT_5_1_MODEL_NAME,
    pricing=(Decimal("1.25"), Decimal("10.00")),
    api_key=OPENAI_API_KEY,
    reasoning_effort="high",
)


def gemini_flash_client() -> LlmClient:
    return _gemini_flash_client


def gemini_pro_client() -> LlmClient:
    return _gemini_pro_client


def gpt_5_1_client() -> LlmClient:
    return _gpt_5_1_client
