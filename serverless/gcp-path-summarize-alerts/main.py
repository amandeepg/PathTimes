from flask import Flask, Request, request

from src.http_handler import summarize_http
from src.cache_view import view_cache_http
from src.pro_worker import summarize_pro_worker

app = Flask(__name__)


@app.route("/summarize", methods=["GET", "POST"])
def summarize_route():
    return summarize_http(request)


@app.route("/pro-worker", methods=["POST"])
def pro_worker_route():
    return summarize_pro_worker(request)


@app.route("/cache", methods=["GET", "POST"])
def cache_route():
    return view_cache_http(request)


@app.route("/")
def health():
    return {"status": "ok"}


# Compatibility: keep callable symbols for Cloud Functions (not used in Cloud Run)
def summarize(req: Request):
    return summarize_http(req)


def summarize_pro_worker_entrypoint(req: Request):
    return summarize_pro_worker(req)


def view_cache(req: Request):
    return view_cache_http(req)
