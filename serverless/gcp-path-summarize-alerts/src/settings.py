import os
from typing import cast

from google.auth import default as google_auth_default  # pyright: ignore[reportUnknownVariableType]
from google.auth.credentials import Credentials


def _default_credentials() -> tuple[Credentials, str | None]:
    credentials, project = google_auth_default()  # pyright: ignore[reportUnknownVariableType]
    return cast(Credentials, credentials), cast(str | None, project)


def get_project_id() -> str:
    env_project = os.getenv("PROJECT_ID") or os.getenv("GOOGLE_CLOUD_PROJECT")
    if env_project:
        return env_project
    _credentials, discovered_project = _default_credentials()
    if not discovered_project:
        raise RuntimeError("Project ID is not set and could not be inferred.")
    return discovered_project


PROJECT_ID = get_project_id()
LOCATION = os.getenv("VERTEX_LOCATION") or os.getenv("LOCATION") or "us-central1"
ENVIRONMENT = os.getenv("ENVIRONMENT") or "staging"

CHEAP_TTL_SECONDS = 300  # 5 minutes
EXPENSIVE_TTL_SECONDS = 86400  # 1 day

PRO_WORKER_URL = os.getenv("PRO_WORKER_URL") or ""
PRO_QUEUE_ID = os.getenv("PRO_QUEUE_ID") or ""
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
CACHE_KEY_PREFIX = os.getenv("CACHE_KEY_PREFIX") or None
