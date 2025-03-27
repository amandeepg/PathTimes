from .llm_client_base import OpenRouterClient, BedrockClient, LlmClient


class R1(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-r1:price"

    def _version(self) -> int:
        return 1


class V3(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-chat-v3-0324:price"

    def _version(self) -> int:
        return 1


class GeminiFlash(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.0-flash-001"

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
    V3(),
    GeminiFlash(),
    O3(),
    GPT4oMini(),
]


FAST_LLM = Llama3dot370b()
PREFERRED_LLM = O3()
