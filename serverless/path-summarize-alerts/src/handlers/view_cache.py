import json
from typing import Optional, List, Dict, Any
from datetime import datetime

import jinja2
from aws_lambda_powertools import Logger
from aws_lambda_powertools.utilities.parser import parse
from aws_lambda_powertools.utilities.typing import LambdaContext
from opentelemetry import trace
from pydantic import BaseModel

from baml_client.types import AffectedStations, AffectedRoutes
from lib.cache import CacheService
from lib.llm_clients import ALL_LLM_CLIENTS
from lib.models import AlertSummaryAiResponse
from lib.summarizer import AlertSummarizer

logger = Logger()
tracer = trace.get_tracer(__name__)


class ViewCacheEventQueryParams(BaseModel):
    key: Optional[str] = None


class ViewCacheEvent(BaseModel):
    queryStringParameters: ViewCacheEventQueryParams


class CacheViewer:
    PAGE_TEMPLATE = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{ title }}</title>
    <link rel="icon" href="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjMyIiBoZWlnaHQ9IjMyIj4KICA8ZGVmcz4KICAgIDxsaW5lYXJHcmFkaWVudCBpZD0iZ3JhZGllbnQiIHgxPSIwJSIgeTE9IjAlIiB4Mj0iMTAwJSIgeTI9IjEwMCUiPgogICAgICA8c3RvcCBvZmZzZXQ9IjAlIiBzdG9wLWNvbG9yPSIjNmY0MmMxIiAvPgogICAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0b3AtY29sb3I9IiMyMTg4ZmYiIC8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogIDwvZGVmcz4KICA8cGF0aCBmaWxsPSJ1cmwoI2dyYWRpZW50KSIgZD0iTTEyIDJsMS41IDMuNUwxOCA3bC0zIDMuNSAxIDUtNC0yLTQgMiAxLTUtMy0zLjUgNC41LTEuNXoiIC8+CiAgPHBhdGggZmlsbD0idXJsKCNncmFkaWVudCkiIGQ9Ik0xOCAxMmwxIDIgMy0xLTEgMyAyIDEtMiAxLTEgMy0xLTItMyAxIDEtMi0yLTEgMy0xeiIgLz4KICA8cGF0aCBmaWxsPSJ1cmwoI2dyYWRpZW50KSIgZD0iTTYgMTJsLTEgMi0zLTEgMSAzLTIgMSAyIDEgMSAzIDEtMiAzIDEtMS0yIDItMS0zLTF6IiAvPgo8L3N2Zz4=" type="image/svg+xml">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/materialize/1.0.0/css/materialize.min.css">
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    {% block styles %}{% endblock %}
</head>
<body>
    <div class="container">
        {% block content %}{% endblock %}
    </div>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/materialize/1.0.0/js/materialize.min.js"></script>
</body>
</html>
"""

    ERROR_TEMPLATE = """
{% extends base_template %}

{% block styles %}
<style>
    .error-container {
        margin-top: 2rem;
        padding: 1.5rem;
        border-radius: 4px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        background-color: #ffebee;
    }
    .error-container h4 {
        color: #d32f2f;
        margin-top: 0;
    }
</style>
{% endblock %}

{% block content %}
<div class="error-container">
    <h4>{{ title }}</h4>
    <p class="flow-text">{{ message }}</p>
</div>
{% endblock %}
"""

    HTML_TEMPLATE = """
{% extends base_template %}

{% block styles %}
<style>
    .page-title {
        padding: 1rem 0;
        color: #424242;
    }
    .key-path {
        color: #757575;
        font-size: 0.9rem;
        margin-bottom: 1.5rem;
    }
    .card-panel.header-panel {
        margin-top: 0;
        background-color: #f5f5f5;
        padding: 12px 24px;
    }
    .data-table {
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        border-radius: 4px;
        overflow: hidden;
    }
    .data-table th {
        background-color: #f5f5f5;
        position: sticky;
        top: 0;
        z-index: 1;
        color: #424242;
        font-weight: 500;
    }
    .data-table td {
        vertical-align: top;
        padding: 12px 16px;
    }
    .divider-row {
        height: 1px;
        background-color: #eeeeee;
    }
    .group-header {
        background-color: #fafafa;
        font-size: 0.85rem;
        color: #757575;
    }
    .chip {
        height: 24px;
        line-height: 24px;
        padding: 0 12px;
        border-radius: 16px;
        margin: 0;
        font-weight: 500;
    }
    .chip.normal {
        background-color: #c8e6c9;
        color: #2e7d32;
    }
    .summary-text {
        max-width: 300px;
        white-space: pre-wrap;
    }
    .input-text {
        max-width: 200px;
        white-space: pre-wrap;
        color: #424242;
        font-weight: 500;
    }
    .model-name {
        font-weight: 500;
        color: #0d47a1;
        font-size: 0.75rem;
    }
    .affected-area {
        font-size: 0.85rem;
    }
</style>
{% endblock %}

{% block content %}
    <h4 class="page-title">LLM Outputs</h4>
    <div class="key-path">{{ hash_category_key }}</div>

    {% if files_by_input %}
    <div class="data-table">
        <table class="responsive-table highlight">
            <thead>
                <tr>
                    <th>Model</th>
                    <th>Input</th>
                    <th>Summary</th>
                    <th>Affected Area</th>
                </tr>
            </thead>
            <tbody>
                {% for input_text, data_group in files_by_input.items() %}
                    {% if not loop.first %}
                    <tr class="divider-row">
                        <td colspan="6"></td>
                    </tr>
                    {% endif %}
                    <tr class="group-header">
                        <td colspan="6">
                            {{ data_group[0].input_string_hash.split('/', 1)[0] }} 
                            <span style="color: #757575; font-size: 0.8em; margin-left: 8px;">
                                ({{ data_group[0].generated_at|format_date }})
                            </span>
                        </td>
                    </tr>
                    {% for data in data_group %}
                        <tr>
                            <td class="model-name">{{ data.model }} ({{ data.model|get_model_price }})</td>
                            <td class="input-text">{% if loop.first %}{{ input_text }}{% endif %}</td>
                            {% if data.response %}
                            <td class="summary-text">{{ data.response.text }}</td>
                            <td class="affected-area">{{ format_affected_area(data.response.affected_area) }}</td>
                            {% else %}
                            <td class="summary-text" colspan="2" style="color: red;">Errored</td>
                            {% endif %}
                        </tr>
                    {% endfor %}
                {% endfor %}
            </tbody>
        </table>
    </div>
    {% else %}
    <div class="card-panel">
        <h5>No Data Available</h5>
        <p>No valid files found in the specified location.</p>
    </div>
    {% endif %}
{% endblock %}
"""

    def __init__(self):
        self._summarizer = AlertSummarizer()
        self._cache_service = CacheService()
        # S3 client removed; using DynamoDB via CacheService

        # Initialize Jinja2 environment
        self._template_env = jinja2.Environment(
            autoescape=True, trim_blocks=True, lstrip_blocks=True
        )

        # Setup templates
        self._template_env.globals["base_template"] = self._template_env.from_string(
            self.PAGE_TEMPLATE
        )
        self._template_env.globals["format_affected_area"] = self._format_affected_area
        self._template_env.filters["get_model_price"] = self._get_model_price
        self._template_env.filters["format_date"] = self._format_generated_date
        self._error_template = self._template_env.from_string(self.ERROR_TEMPLATE)
        self._main_template = self._template_env.from_string(self.HTML_TEMPLATE)

    @staticmethod
    def _format_generated_date(epoch: int) -> str:
        dt = datetime.fromtimestamp(epoch) if epoch else None
        return dt.strftime("%B %d, %Y %I:%M:%S %p").replace(" 0", " ") if dt else "N/A"

    def handle(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """Renders HTML with a table showing data from the DynamoDB cache."""
        logger.info("Received view_cache request")
        logger.debug(f"Event: {json.dumps(event)}")

        # Ensure queryStringParameters exists for parsing, default to empty if not present
        if "queryStringParameters" not in event or not event["queryStringParameters"]:
            event["queryStringParameters"] = {}
        parsed_event: ViewCacheEvent = parse(model=ViewCacheEvent, event=event)

        try:
            return self._create_cache_view_response(
                parsed_event.queryStringParameters.key
            )
        except Exception as e:
            logger.exception("Error processing view_cache request")
            return {
                "statusCode": 500,
                "body": json.dumps({"error": str(e)}),
                "headers": {"Content-Type": "application/json"},
            }

    @tracer.start_as_current_span("create_cache_view_response")
    def _create_cache_view_response(
        self, hash_category_key: Optional[str] = None
    ) -> Dict[str, Any]:
        """Creates HTML response with data from DynamoDB."""
        prefix = (
            hash_category_key
            if hash_category_key
            else self._cache_service.hash_category_key()
        )

        try:
            # Query DynamoDB for items where hash_key begins with the prefix (hash_category_key)
            items = self._cache_service.query_all(prefix)
            if not items:
                error_html = self._error_template.render(
                    title="Not Found", message=f"No items found for {prefix}"
                )
                return {
                    "statusCode": 404,
                    "body": error_html,
                    "headers": {"Content-Type": "text/html"},
                }

            # Sort items by generated_at descending
            sorted_items = sorted(
                items,
                key=lambda x: x.generated_at,
                reverse=True,
            )
            html_content = self._generate_html_table(
                sorted_items,
                prefix,
            )
            return {
                "statusCode": 200,
                "body": html_content,
                "headers": {"Content-Type": "text/html"},
            }

        except Exception as e:
            logger.exception(f"Error querying DynamoDB: {str(e)}")
            error_html = self._error_template.render(title="Error", message=str(e))  # type: ignore
            return {
                "statusCode": 500,
                "body": error_html,
                "headers": {"Content-Type": "text/html"},
            }

    # Async fetch method removed; DynamoDB items are processed directly in _create_cache_view_response

    def _generate_html_table(
        self, files_data: List[AlertSummaryAiResponse], hash_category_key: str
    ) -> str:
        """Generates HTML table from list of CacheResponse objects using Jinja2."""

        if not files_data:
            return self._error_template.render(  # type: ignore
                title="No Data", message="No valid files found"
            )

        # Extract the model IDs into a set for efficient lookup
        all_llm_client_ids = {client.id() for client in ALL_LLM_CLIENTS}

        files_by_input: Dict[str, List[AlertSummaryAiResponse]] = {}
        for data in files_data:
            # Skip test inputs
            if "testinput" in data.input:
                continue
            # Skip data if it's None or the model is not recognized
            if data.model not in all_llm_client_ids:
                continue
            # Group by input text
            if data.input not in files_by_input:
                files_by_input[data.input] = []
            files_by_input[data.input].append(data)

        # Sort each group by model cost (ascending)
        for input_text in files_by_input:
            files_by_input[input_text].sort(
                key=lambda data: self._get_model_cost_float(data.model)
            )

        return self._main_template.render(  # type: ignore
            files_by_input=files_by_input,
            hash_category_key=hash_category_key,
            title="LLM Outputs",
        )

    @staticmethod
    def _get_model_cost_float(model_id: str) -> float:
        """Gets the numerical cost of a model."""
        for client in ALL_LLM_CLIENTS:
            if client.id() == model_id:
                try:
                    cost = client.cost()
                    # Ensure cost is a number, default to 0.0 if None or invalid
                    return float(cost or 0.0)
                except (ValueError, TypeError):
                    logger.warning(
                        f"Could not convert cost for model {model_id} to float."
                    )
                    return 0.0
        logger.warning(f"Could not find cost for model: {model_id}")
        return 0.0  # Return 0.0 if model not found or has no cost defined

    @staticmethod
    def _format_affected_area(
        affected_area: AffectedStations | AffectedRoutes | None,
    ) -> str:
        if isinstance(affected_area, AffectedStations):
            return f"Stations: {', '.join(str(s).replace('PathStation.', '') for s in affected_area.affected_stations)}"
        elif isinstance(affected_area, AffectedRoutes):
            return f"Routes: {', '.join(str(r).replace('PathLine.', '') for r in affected_area.affected_routes)}"
        else:
            return "None"

    @staticmethod
    def _get_model_price(model_id: str) -> str:
        """Gets the formatted price string for a model."""
        # find model_id in ALL_LLM_CLIENTS.map(id)
        for client in ALL_LLM_CLIENTS:
            if client.id() == model_id:
                try:
                    # format in $x.xx
                    cost = client.cost()
                    return f"${float(cost):.2f}"
                except (ValueError, TypeError):
                    logger.warning(f"Could not format cost for model {model_id}.")
                    return "$?.??"  # Indicate unknown cost
        return "$0.00"  # Default if model not found


handler = CacheViewer()


@logger.inject_lambda_context
def handle(event: Dict[str, Any], context: LambdaContext) -> Dict[str, Any]:
    return handler.handle(event)
