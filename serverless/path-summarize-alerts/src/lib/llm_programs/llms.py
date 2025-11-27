from typing import Any
import dspy  # pyright: ignore[reportMissingTypeStubs]
import os
from enum import Enum

# Set up OpenAI Model
openrouter_key = os.environ.get("OPENROUTER_API_KEY")
if not os.environ.get("OPENROUTER_API_KEY"):
    raise ValueError("OPENROUTER_API_KEY environment variable not set.")


class LLM(Enum):
    GPT_OSS_20B = (
        "openrouter/openai/gpt-oss-20b:price",
        False,
        0.0,
    )  # model, use_reasoning, temperature
    GPT_OSS_120B = ("openrouter/openai/gpt-oss-120b:price", False, 0.0)
    GEMINI_FLASH = ("openrouter/google/gemini-2.5-flash", True, 1.0)
    GEMINI_FLASH_NO_REASONING = ("openrouter/google/gemini-2.5-flash", False, 1.0)
    GEMINI_FLASH_LITE = ("openrouter/google/gemini-2.5-flash-lite", False, 0.0)
    GEMINI_PRO = ("openrouter/google/gemini-2.5-pro", True, 1.0)
    GPT5 = ("openrouter/openai/gpt-5", True, 1.0)
    GPT5_NANO = ("openrouter/openai/gpt-5-nano", True, 1.0)
    QWEN3_32B = ("openrouter/qwen/qwen3-32b:price", False, 0.0)
    QWEN3_235B = ("openrouter/qwen/qwen3-235b-a22b-2507:price", False, 0.0)
    QWEN3_30B = ("openrouter/qwen/qwen3-30b-a3b-instruct-2507:price", False, 0.0)
    NEMOTRON_NANO_9B_V2 = ("openrouter/nvidia/nemotron-nano-9b-v2", False, 0.0)

    def __init__(self, model: str, use_reasoning: bool, temperature: float):
        self.model = model
        self.use_reasoning = use_reasoning
        self.temperature = temperature

        params: dict[str, Any] = {
            "model": self.model,
            "api_base": "https://openrouter.ai/api/v1",
            "api_key": openrouter_key,
            "temperature": self.temperature,
        }

        if self.use_reasoning:
            params["reasoning"] = {"max_tokens": 16000}
            params["max_tokens"] = 16000

        self.lm = dspy.LM(**params)
