import os
from abc import ABC
from abc import abstractmethod
from typing import Any, Dict

from baml_py import ClientRegistry


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

    @abstractmethod
    def cost(self) -> float:
        pass

    def _add_to_registry(
        self, cr: ClientRegistry, provider: str, options: Dict[str, Any]
    ):
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
                "temperature": 0.0,
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
                "inference_configuration": {"temperature": 0.0},
            },
        )
