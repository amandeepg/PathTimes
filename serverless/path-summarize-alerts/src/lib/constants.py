import os
from abc import ABC
from abc import abstractmethod

from baml_py import ClientRegistry

BUCKET_NAME = "path-summarize-data"
BUCKET_NAME_RATE_LIMIT = "path-summarize-data-rate-limit"
CACHE_INT = "2"


class LlmClient(ABC):
    @abstractmethod
    def add_to_registry(self, cr: ClientRegistry):
        pass

    def id(self) -> str:
        return f"{self._name()}-{self._version()}"

    @abstractmethod
    def _name(self) -> str:
        pass

    @abstractmethod
    def _version(self) -> int:
        pass

    def _add_to_registry(self, cr: ClientRegistry, provider: str, options: dict):
        cr.add_llm_client(
            name=self.id(),
            provider=provider,
            options=options,
        )


class OpenRouterClient(LlmClient, ABC):
    @abstractmethod
    def model(self) -> str:
        pass

    def _name(self) -> str:
        return self.model()

    def add_to_registry(self, cr: ClientRegistry):
        self._add_to_registry(
            cr=cr,
            provider="openai-generic",
            options={
                "model": self.model(),
                "temperature": 0.4,
                "api_key": os.environ.get("OPENROUTER_API_KEY"),
                "base_url": "https://openrouter.ai/api/v1",
                "headers": {
                    "HTTP-Referer": os.environ.get("OPENROUTER_APP_URL"),
                    "X-Title": os.environ.get("OPENROUTER_APP_NAME"),
                },
            },
        )


class BedrockClient(LlmClient):
    @abstractmethod
    def model(self) -> str:
        pass

    def _name(self) -> str:
        return self.model()

    def add_to_registry(self, cr: ClientRegistry):
        self._add_to_registry(
            cr=cr,
            provider="aws-bedrock",
            options={
                "region": "us-east-1",
                "model": self.model(),
                "inference_configuration": {"temperature": 0.4},
            },
        )


class R1(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-r1"

    def _version(self) -> int:
        return 1


class V3(OpenRouterClient):
    def model(self) -> str:
        return "deepseek/deepseek-chat"

    def _version(self) -> int:
        return 1


class GeminiFlash(OpenRouterClient):
    def model(self) -> str:
        return "google/gemini-2.0-flash-001"

    def _version(self) -> int:
        return 1


class LlamaThreeThree70b(BedrockClient):
    def model(self) -> str:
        return "us.meta.llama3-3-70b-instruct-v1:0"

    def _version(self) -> int:
        return 1


class O3(OpenRouterClient):
    def model(self) -> str:
        return "openai/o3-mini-high"

    def _version(self) -> int:
        return 1


class NovaLite(BedrockClient):
    def model(self) -> str:
        return "amazon.nova-lite-v1:0"

    def _version(self) -> int:
        return 1


ALL_LLM_CLIENTS: list[LlmClient] = [
    R1(),
    V3(),
    GeminiFlash(),
    LlamaThreeThree70b(),
    O3(),
    NovaLite(),
]
