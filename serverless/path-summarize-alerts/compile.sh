#!/usr/bin/env bash

uv sync

uv run baml-cli generate

rm src/lib/hash_constants.py
uv run src/update_hash.py

rm src/lib/llm_clients.py
uv run src/update_llms.py

uvx ruff format
uvx ruff check --fix
# uvx pyright
