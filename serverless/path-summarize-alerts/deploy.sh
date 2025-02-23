#!/usr/bin/env bash

uvx ruff format
uvx ruff check

rm requirements.txt
uv pip freeze > requirements.txt

rm src/lib/hash_constants.py
uv run src/update_hash.py

sls deploy