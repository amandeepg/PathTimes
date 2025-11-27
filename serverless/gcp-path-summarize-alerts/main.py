from flask import Request

from src.http_handler import summarize_http
from src.pro_worker import summarize_pro_worker


def summarize(request: Request):
    return summarize_http(request)


def summarize_pro_worker_entrypoint(request: Request):
    return summarize_pro_worker(request)
