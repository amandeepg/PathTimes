import json
import logging
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
    logger: logging.Logger | logging.LoggerAdapter[logging.Logger] | None = None,
) -> None:
    """Fire-and-forget: enqueue a task to build the expensive summary."""
    logger = logger or logging.getLogger("tasks")
    if not queue_id or not worker_url:
        logger.info(
            "pro_task_enqueue_skipped %s",
            {"queue_id": queue_id, "worker_url": worker_url},
        )
        return
    logger.info(
        "pro_task_enqueue_start %s",
        {
            "cache_key": cache_key,
            "text_length": len(text),
            "project_id": project_id,
            "region": region,
            "queue_id": queue_id,
            "worker_url": worker_url,
        },
    )
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
        response = client.create_task(  # pyright: ignore[reportUnknownMemberType]
            request={"parent": parent, "task": task}
        )
        logger.info(
            "pro_task_enqueued %s",
            {
                "task_name": response.name,
                "queue": parent,
                "cache_key": cache_key,
                "worker_url": worker_url,
            },
        )
    except Exception as exc:
        # Best-effort enqueue; failure should not break the request path.
        logger.exception(
            "pro_task_enqueue_failed %s",
            {
                "cache_key": cache_key,
                "queue": parent,
                "worker_url": worker_url,
                "error_type": type(exc).__name__,
            },
        )
        return
