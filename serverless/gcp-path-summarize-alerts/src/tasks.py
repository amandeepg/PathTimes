import json
from typing import Final

from google.cloud import tasks_v2


def enqueue_pro_task(
    *,
    text: str,
    cache_key: str,
    project_id: str,
    region: str,
    queue_id: str,
    worker_url: str,
) -> None:
    """Fire-and-forget: enqueue a task to build the expensive summary."""
    if not queue_id or not worker_url:
        return
    client = tasks_v2.CloudTasksClient()
    parent: Final[str] = client.queue_path(
        project=project_id, location=region, queue=queue_id
    )
    task = tasks_v2.Task(
        http_request=tasks_v2.HttpRequest(
            http_method=tasks_v2.HttpMethod.POST,
            url=worker_url,
            headers={"Content-Type": "application/json"},
            body=json.dumps({"text": text, "cache_key": cache_key}).encode("utf-8"),
        )
    )
    try:
        _ = client.create_task(request={"parent": parent, "task": task})  # pyright: ignore[reportUnknownMemberType]
    except Exception:
        # Best-effort enqueue; failure should not break the request path.
        return
