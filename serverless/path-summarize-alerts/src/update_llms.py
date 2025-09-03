from typing import Any, Dict, List

import tomli


def generate_llm_class(
    llm_name: str, llm_config: Dict[str, Any], client_class: str
) -> str:
    model = llm_config["model"]
    version = llm_config.get("version", 1)  # Default to 1 if version is not specified
    cost = llm_config.get("cost", float(0.0))

    class_template = f"""
class {llm_name}({client_class}):
    def model(self) -> str:
        return "{model}"

    def _version(self) -> int:
        return {version}

    def cost(self) -> float:
        return {cost}
"""
    return class_template


def generate_all_llm_clients(llms: List[str]) -> str:
    all_llm_clients: List[str] = []
    for llm_name in llms:
        all_llm_clients.append(f"{llm_name}()")
    return (
        "ALL_LLM_CLIENTS: list[LlmClient] = [\n    "
        + ",\n    ".join(all_llm_clients)
        + ",\n]"
    )


def generate_preferences(preferences: Dict[str, str]) -> str:
    fast_llm = preferences["fast_llm"]
    preferred_llm = preferences["preferred_llm"]
    return f"""
FAST_LLM = {fast_llm}()
PREFERRED_LLM = {preferred_llm}()
"""


def generate_python_file(config: Dict[str, Any]) -> None:
    openrouter_llms = config["openrouter"]
    bedrock_llms = config["bedrock"]
    preferences = config["preferences"]

    with open("src/lib/llm_clients.py", "w") as f:
        f.write(
            "from .llm_client_base import OpenRouterClient, BedrockClient, LlmClient\n\n"
        )

        # Generate OpenRouterClient classes
        for llm_name, llm_config in openrouter_llms.items():
            f.write(generate_llm_class(llm_name, llm_config, "OpenRouterClient"))

        # Generate BedrockClient classes
        for llm_name, llm_config in bedrock_llms.items():
            f.write(generate_llm_class(llm_name, llm_config, "BedrockClient"))

        # Generate ALL_LLM_CLIENTS list
        all_llms = list(openrouter_llms.keys()) + list(bedrock_llms.keys())
        f.write("\n")
        f.write(generate_all_llm_clients(all_llms))
        f.write("\n\n")

        # Generate preferences
        f.write(generate_preferences(preferences))


if __name__ == "__main__":
    with open("llms.toml", "rb") as f:
        config = tomli.load(f)
    generate_python_file(config)
