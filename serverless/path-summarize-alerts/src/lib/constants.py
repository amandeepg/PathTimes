from enum import Enum

BUCKET_NAME = "path-summarize-data"
BUCKET_NAME_RATE_LIMIT = "path-summarize-data-rate-limit"
CACHE_INT = "2"


class OpenRouterClient(Enum):
    R1 = ("r1", "deepseek/deepseek-r1")
    V3 = ("v3", "deepseek/deepseek-chat")
    GEMINI_FLASH = ("gemini-flash", "google/gemini-2.0-flash-001")
    LLAMA = ("llama", "meta-llama/llama-3.3-70b-instruct")
    O3 = ("o3", "openai/o3-mini-high")
