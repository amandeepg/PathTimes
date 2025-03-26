from .llm_client_base import OpenRouterClient, BedrockClient, LlmClient


class R1(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-r1:price"

    def _version(self) -> int:
        return 1


class R1Llama70B(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-r1-distill-llama-70b:price"

    def _version(self) -> int:
        return 1


class V3(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-chat:price"

    def _version(self) -> int:
        return 1


class Claude3dot7Thinking(OpenRouterClient):
    def model(self) -> str:
        return "anthropic/claude-3.7-sonnet:thinking"

    def _version(self) -> int:
        return 1


class Qwq32B(OpenRouterClient):
    def model(self) -> str:
        return "qwen/qwq-32b:price"

    def _version(self) -> int:
        return 1


class Phi4(OpenRouterClient):
    def model(self) -> str:
        return "microsoft/phi-4:price"

    def _version(self) -> int:
        return 1


class GeminiFlash(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.0-flash-001"

    def _version(self) -> int:
        return 1


class GeminiFlashLite(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.0-flash-lite-001"

    def _version(self) -> int:
        return 1


class O3(OpenRouterClient):
    def model(self) -> str:
        return "openai/o3-mini-high"

    def _version(self) -> int:
        return 1


class GPT4oMini(OpenRouterClient):
    def model(self) -> str:
        return "openai/gpt-4o-mini"

    def _version(self) -> int:
        return 1


class Llama3dot370b(BedrockClient):
    def model(self) -> str:
        return "us.meta.llama3-3-70b-instruct-v1:0"

    def _version(self) -> int:
        return 1


ALL_LLM_CLIENTS: list[LlmClient] = [
    R1(),
    R1Llama70B(),
    V3(),
    Claude3dot7Thinking(),
    Qwq32B(),
    Phi4(),
    GeminiFlash(),
    GeminiFlashLite(),
    O3(),
    GPT4oMini(),
]


FAST_LLM = Llama3dot370b()
PREFERRED_LLM = R1()
