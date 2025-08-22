from .llm_client_base import OpenRouterClient, BedrockClient, LlmClient


class R1(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-r1-0528:floor"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.45


class V3(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-chat-v3.1:floor"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.4


class GeminiTwoDotFivePro(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.5-pro"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 6.0


class GeminiTwoDotFiveFlash(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.5-flash"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 1.45


class GeminiTwoDotFiveFlashLite(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.5-flash-lite"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.25


class QwenThreeThinking(OpenRouterClient):
    def model(self) -> str:
        return "qwen/qwen3-235b-a22b-thinking-2507:floor"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.2


class GptOss20(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-oss-20b:floor"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.1


class GptOss120(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-oss-120b:floor"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.18


class KimiK2(OpenRouterClient):
    def model(self) -> str:
        return "moonshotai/kimi-k2:floor"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.37


class Llama3dot370b(BedrockClient):
    def model(self) -> str:
        return "us.meta.llama3-3-70b-instruct-v1:0"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.72


ALL_LLM_CLIENTS: list[LlmClient] = [
    R1(),
    V3(),
    GeminiTwoDotFivePro(),
    GeminiTwoDotFiveFlash(),
    GeminiTwoDotFiveFlashLite(),
    QwenThreeThinking(),
    GptOss20(),
    GptOss120(),
    KimiK2(),
    Llama3dot370b(),
]


FAST_LLM = Llama3dot370b()
PREFERRED_LLM = GeminiTwoDotFivePro()
