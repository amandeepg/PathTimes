#!/usr/bin/env bash

uv sync

uv run baml-cli generate

rm src/lib/hash_constants.py
uv run src/update_hash.py

rm src/lib/llm_clients.py
uv run src/update_llms.py

uvx ruff format
uvx ruff check --fix

rm requirements.txt
uv pip freeze > requirements.txt

rm -rf streamlit-viewcache/lib
cp -r src/lib streamlit-viewcache/lib
rm -rf streamlit-viewcache/baml_client
cp -r src/baml_client streamlit-viewcache/baml_client

find streamlit-viewcache/lib -type f -exec sed -i 's/from ..baml_client.types/from baml_client.types/g' {} +
