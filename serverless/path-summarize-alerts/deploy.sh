#!/usr/bin/env bash

uv run baml-cli generate
uvx ruff format
uvx ruff check --fix

rm requirements.txt
uv pip freeze > requirements.txt

rm src/lib/hash_constants.py
uv run src/update_hash.py

sls deploy