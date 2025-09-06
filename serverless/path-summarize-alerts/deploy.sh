#!/usr/bin/env bash

bash compile.sh

uv sync --no-dev
rm requirements.txt
uv pip freeze > requirements.txt
uv pip freeze > src/requirements.txt

sam build --use-container
sam deploy

uv sync
