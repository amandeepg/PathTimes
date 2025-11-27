import logging

from flask import Request

from .settings import PROJECT_ID

# Configure root logging to INFO so application logs are emitted to stdout/Cloud Logging.
logging.basicConfig(level=logging.INFO, force=True)


def _extract_trace(request: Request) -> str | None:
    header = request.headers.get("X-Cloud-Trace-Context", "")
    if not header:
        return None
    trace_id = header.split("/", 1)[0]
    if not trace_id:
        return None
    return f"projects/{PROJECT_ID}/traces/{trace_id}"


def request_logger(
    request: Request, *, name: str = "app"
) -> logging.LoggerAdapter[logging.Logger]:
    """Return a logger that includes Cloud Trace context when available."""
    trace = _extract_trace(request)
    base_logger = logging.getLogger(name)
    extra: dict[str, object] = {}
    if trace:
        extra["trace"] = trace
    return logging.LoggerAdapter(base_logger, extra)
