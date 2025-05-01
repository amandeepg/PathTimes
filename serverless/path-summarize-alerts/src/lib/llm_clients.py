from .llm_client_base import OpenRouterClient, BedrockClient, LlmClient


class R1(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-r1:price"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 1.4


class V3(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-chat-v3-0324:price"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.7


class GeminiTwoDotFivePro(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.5-pro-preview-03-25"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 6.0


class GeminiTwoDotFiveFlash(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.5-flash-preview"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.37


class GPT4Dot1(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-4.1"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 5.0


class GPT4Dot1Mini(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-4.1-mini"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 1.0


class GPT4Dot1Nano(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-4.1-nano"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.25


class O4mini(OpenRouterClient):
    def model(self) -> str:
        return "openai/o4-mini"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 2.5


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
    GPT4Dot1(),
    GPT4Dot1Mini(),
    GPT4Dot1Nano(),
    O4mini(),
    Llama3dot370b(),
]


FAST_LLM = Llama3dot370b()
PREFERRED_LLM = GeminiTwoDotFivePro()
