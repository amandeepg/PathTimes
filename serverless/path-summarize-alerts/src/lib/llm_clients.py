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


class GeminiFlash(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.0-flash-001"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.3


class GPT4oMini(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-4o-mini"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.5


class GPT4o(OpenRouterClient):
    def model(self) -> str:
        return "openai/chatgpt-4o-latest"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 10.0


class Haiku(OpenRouterClient):
    def model(self) -> str:
        return "anthropic/claude-3.5-haiku"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 2.4


class Maverick(OpenRouterClient):
    def model(self) -> str:
        return "meta-llama/llama-4-maverick"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.4


class Scout(OpenRouterClient):
    def model(self) -> str:
        return "meta-llama/llama-4-scout"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.3


class GeminiTwoFive(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.5-pro-preview-03-25"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 6.0


class OptimusAlpha(OpenRouterClient):
    def model(self) -> str:
        return "openrouter/optimus-alpha"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.0


class Mistral(OpenRouterClient):
    def model(self) -> str:
        return "mistral/ministral-8b"

    def _version(self) -> int:
        return 1

    def cost(self) -> float:
        return 0.1


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
    GeminiFlash(),
    GPT4oMini(),
    GPT4o(),
    Haiku(),
    Maverick(),
    Scout(),
    GeminiTwoFive(),
    OptimusAlpha(),
    Mistral(),
    Llama3dot370b(),
]


FAST_LLM = Llama3dot370b()
PREFERRED_LLM = GeminiTwoFive()
