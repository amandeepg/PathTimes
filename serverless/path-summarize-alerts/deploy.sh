#!/usr/bin/env bash

bash compile.sh
sam build --use-container
sam deploy
