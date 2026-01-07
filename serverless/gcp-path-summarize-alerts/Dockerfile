# syntax=docker/dockerfile:1.5

FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8080

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/*

COPY . .
RUN pip install -r requirements.txt

# Use gunicorn for production serving
CMD ["gunicorn", "-w", "2", "-k", "gthread", "-b", ":8080", "main:app"]
