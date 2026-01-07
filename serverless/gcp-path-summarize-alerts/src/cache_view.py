from __future__ import annotations

import html
from collections.abc import Iterable
from datetime import datetime, timezone
from typing import Literal, Protocol, TypedDict, cast
from zoneinfo import ZoneInfo

from flask import Request
from google.cloud import firestore  # pyright: ignore[reportMissingTypeStubs]
from pydantic import ValidationError

from .cache import CHEAP_COLLECTION_NAME, EXPENSIVE_COLLECTION_NAME
from .models import AlertSummaryContainer
from .settings import ENVIRONMENT, PROJECT_ID


class _DocumentSnapshot(Protocol):
    id: str
    exists: bool

    def to_dict(self) -> dict[str, object] | None: ...


class _DocumentReference(Protocol):
    def get(self) -> _DocumentSnapshot: ...

    def delete(self) -> None: ...


class CacheEntry(TypedDict):
    collection: Literal["cheap", "expensive"]
    cache_key: str
    text: str
    summary: str
    llm_type: str
    created_at: datetime | None
    stations: list[str]
    lines: list[str]


DB: firestore.Client = firestore.Client(project=PROJECT_ID)

MAX_RESULTS_DEFAULT = 200


def view_cache_http(request: Request):
    """HTML view of cached summaries."""
    message: str | None = None
    if request.method == "POST":
        keys = request.form.getlist("cache_key")
        deleted = _delete_entries(keys)
        message = (
            f"Deleted {deleted} entr{'y' if deleted == 1 else 'ies'}."
            if deleted
            else "No entries deleted."
        )

    prefix = (
        (request.args.get("prefix") if request.args else None)
        or (request.form.get("prefix") if request.method == "POST" else None)
        or None
    )
    limit_raw = (
        (request.args.get("limit") if request.args else None)
        or (request.form.get("limit") if request.method == "POST" else None)
        or ""
    )
    try:
        limit = int(limit_raw)
    except ValueError:
        limit = MAX_RESULTS_DEFAULT
    limit = max(1, min(limit or MAX_RESULTS_DEFAULT, 500))

    entries = list(_load_entries(prefix=prefix, limit=limit))
    body = _render_page(entries, prefix, limit, message)
    return body, 200, {"Content-Type": "text/html"}


def _load_entries(*, prefix: str | None, limit: int) -> list[CacheEntry]:
    entries: list[CacheEntry] = []
    for collection_name in (CHEAP_COLLECTION_NAME, EXPENSIVE_COLLECTION_NAME):
        doc_count = 0
        docs_iter = cast(
            Iterable[_DocumentSnapshot], DB.collection(collection_name).stream()
        )
        for doc in docs_iter:
            if doc_count >= limit:
                break
            if prefix and not doc.id.startswith(prefix):
                continue
            data_raw = doc.to_dict()
            data: dict[str, object] = data_raw if isinstance(data_raw, dict) else {}
            result_data = data.get("result")
            result: AlertSummaryContainer | None = None
            if isinstance(result_data, dict):
                try:
                    result = AlertSummaryContainer.model_validate(result_data)
                except ValidationError:
                    result = None
            summary_value = result.text if result else data.get("summary")
            summary_text = str(summary_value or "")
            created_at_raw = data.get("created_at")
            created_at = (
                created_at_raw if isinstance(created_at_raw, datetime) else None
            )
            lines = (
                [line.value for line in result.affected_lines.affected_lines]
                if result and result.affected_lines
                else []
            )
            stations = (
                [
                    station.value
                    for station in result.affected_stations.affected_stations
                ]
                if result and result.affected_stations
                else []
            )
            entries.append(
                {
                    "collection": (
                        "expensive"
                        if collection_name == EXPENSIVE_COLLECTION_NAME
                        else "cheap"
                    ),
                    "cache_key": doc.id,
                    "text": str(data.get("text") or ""),
                    "summary": summary_text,
                    "llm_type": str(data.get("llm_type") or "unknown"),
                    "created_at": created_at,
                    "stations": stations,
                    "lines": lines,
                }
            )
            doc_count += 1

    def _created(entry: CacheEntry) -> datetime:
        value = entry["created_at"]
        if isinstance(value, datetime):
            return value
        return datetime.fromtimestamp(0, tz=timezone.utc)

    entries.sort(key=_created, reverse=True)
    return entries[:limit]


def _fmt_dt(value: datetime | None) -> str:
    if isinstance(value, datetime):
        eastern = value.astimezone(ZoneInfo("America/New_York"))
        return (
            eastern.strftime("%b %-d at %-I:%M%p ET")
            .replace("AM", "am")
            .replace("PM", "pm")
        )
    return "—"


def _pastel_color(name: str) -> str:
    """Deterministic pastel color for a label name."""
    h = hash(name) % 360
    return f"hsl({h}, 65%, 82%)"


def _line_color(line: str) -> str:
    colors = {
        "NWK_WTC": "#ef3941",  # Red
        "HOB_WTC": "#009e58",  # Green
        "JSQ_33": "#fdb827",  # Yellow
        "HOB_33": "#0082c6",  # Blue
        "JSQ_33_HOB": "linear-gradient(90deg, #fdb827 0%, #fdb827 50%, #0082c6 50%, #0082c6 100%)",
        "JSQ_WTC": "#ef3941",
    }
    return colors.get(line, "#94a3b8")


def _line_label(line: str) -> str:
    labels = {
        "NWK_WTC": "NWK–WTC",
        "HOB_WTC": "HOB–WTC",
        "JSQ_33": "JSQ–33",
        "HOB_33": "HOB–33",
        "JSQ_33_HOB": "JSQ–33 via HOB",
        "JSQ_WTC": "JSQ–WTC",
    }
    return labels.get(line, line)


def _delete_entries(keys: list[str]) -> int:
    if not keys:
        return 0
    deleted = 0
    for key in keys:
        for collection_name in (CHEAP_COLLECTION_NAME, EXPENSIVE_COLLECTION_NAME):
            doc_ref = cast(
                _DocumentReference,
                cast(object, DB.collection(collection_name).document(key)),
            )
            doc = doc_ref.get()
            if doc.exists:
                doc_ref.delete()
                deleted += 1
    return deleted


def _render_page(
    entries: list[CacheEntry], prefix: str | None, limit: int, message: str | None
) -> str:
    title = "PATH Summaries Cache"
    filter_text = (
        f"Prefix filter: {html.escape(prefix)}" if prefix else "Prefix: (none)"
    )
    rows: list[str] = []
    if not entries:
        rows.append(
            '<tr><td colspan="5" class="empty">No cached alerts found for this project.</td></tr>'
        )
    else:
        # group by cache_key preserving order
        grouped: dict[str, list[CacheEntry]] = {}
        for entry in entries:
            grouped.setdefault(str(entry["cache_key"]), []).append(entry)

        for cache_key, group_entries in grouped.items():
            rows.append(
                f"""<tr class="group-header">
                      <td colspan="5">
                        <label class="select-row">
                          <input type="checkbox" name="cache_key" value="{html.escape(cache_key)}">
                          <span></span>
                        </label>
                        <div class="cache-key">{html.escape(cache_key)}</div>
                      </td>
                    </tr>"""
            )
            for entry in group_entries:
                stations = entry["stations"]
                lines = entry["lines"]
                line_pills = (
                    "".join(
                        '<span class="tag line" style="background:{bg};">{label}</span>'.format(
                            bg=_line_color(str(line)),
                            label=html.escape(_line_label(str(line))),
                        )
                        for line in lines
                    )
                    if lines
                    else ""
                )
                station_pills = (
                    "".join(
                        '<span class="tag soft" style="background:{bg};">{label}</span>'.format(
                            bg=_pastel_color(str(station)),
                            label=html.escape(str(station)),
                        )
                        for station in stations
                    )
                    if stations
                    else ""
                )
                chips = (line_pills + station_pills) or "—"
                rows.append(
                    f"""
                    <tr>
                        <td class="pill {html.escape(entry["collection"])}">{html.escape(entry["collection"])}</td>
                        <td>{html.escape(entry["summary"])}</td>
                        <td>{html.escape(entry["text"])}</td>
                        <td class="chips">{chips}</td>
                        <td>{_fmt_dt(entry["created_at"])}</td>
                    </tr>
                    """
                )

    rows_html = "\n".join(rows)
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>{title}</title>
  <style>
    :root {{
      --bg: #0f172a;
      --card: #111827;
      --text: #e2e8f0;
      --muted: #94a3b8;
      --accent: #22c55e;
      --cheap: #22c55e33;
      --expensive: #f9731633;
      --border: #1f2937;
      --tag-text: #0b1222;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: "Inter", "Segoe UI", system-ui, -apple-system, sans-serif;
      background: radial-gradient(circle at 20% 20%, #0b1324, #060a14 40%), var(--bg);
      color: var(--text);
      padding: 24px;
    }}
    h1 {{
      margin: 0 0 8px 0;
      font-size: 24px;
      letter-spacing: -0.02em;
    }}
    .meta {{
      color: var(--muted);
      margin-bottom: 16px;
      font-size: 14px;
    }}
    .card {{
      background: linear-gradient(135deg, #0f172a 0%, #0b1222 100%);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 16px;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.35);
    }}
    table {{
      width: 100%;
      border-collapse: collapse;
      margin-top: 12px;
    }}
    th, td {{
      padding: 10px 12px;
      text-align: left;
      vertical-align: top;
      font-size: 13px;
    }}
    th {{
      color: var(--muted);
      position: sticky;
      top: 0;
      background: #0d1526;
      border-bottom: 1px solid var(--border);
      z-index: 1;
    }}
    .group-header td {{
      background: rgba(255,255,255,0.03);
      border-bottom: 1px solid var(--border);
    }}
    .cache-key {{
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
      font-size: 13px;
      color: var(--muted);
    }}
    tr:nth-child(even) td {{
      background: rgba(255, 255, 255, 0.02);
    }}
    td {{
      border-bottom: 1px solid var(--border);
    }}
    .mono {{ font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace; }}
    .pill {{
      display: inline-flex;
      align-items: center;
      padding: 4px 10px;
      border-radius: 999px;
      font-weight: 600;
      text-transform: capitalize;
      color: #0b1222;
      background: var(--accent);
    }}
    .pill.expensive {{ background: #f97316; color: #0b1222; }}
    .pill.cheap {{ background: #22c55e; color: #0b1222; }}
    .chips {{ display: flex; gap: 6px; flex-wrap: wrap; }}
    .tag {{
      display: inline-flex;
      padding: 4px 10px;
      border-radius: 999px;
      font-weight: 600;
      color: var(--tag-text);
      border: 1px solid rgba(0,0,0,0.06);
      box-shadow: 0 2px 6px rgba(0,0,0,0.12);
    }}
    .tag.soft {{
      opacity: 0.9;
    }}
    .empty {{
      text-align: center;
      color: var(--muted);
      padding: 24px;
      font-size: 15px;
    }}
    .controls {{
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
      color: var(--muted);
      font-size: 13px;
    }}
    .flash {{
      margin: 8px 0 12px 0;
      padding: 10px 12px;
      border-radius: 8px;
      background: #133022;
      color: #bbf7d0;
      border: 1px solid #14532d;
      font-size: 13px;
    }}
    .actions {{
      margin-top: 12px;
      display: flex;
      gap: 12px;
    }}
    .btn {{
      background: #2563eb;
      color: white;
      border: none;
      padding: 10px 14px;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
    }}
    .btn.danger {{
      background: #ef4444;
    }}
    .btn:hover {{
      opacity: 0.9;
    }}
    .select-row {{
      display: inline-flex;
      align-items: center;
      margin-right: 10px;
    }}
    .select-row input {{
      width: 16px;
      height: 16px;
    }}
    .tag.line {{
      color: #ffffff;
    }}
    a {{ color: #38bdf8; text-decoration: none; }}
    a:hover {{ text-decoration: underline; }}
  </style>
</head>
<body>
  <div class="card">
    <h1>{title}</h1>
    <div class="meta">Environment: <strong>{html.escape(ENVIRONMENT)}</strong> · Project: <strong>{html.escape(PROJECT_ID)}</strong> · Showing up to {limit} per collection</div>
    {"<div class='flash'>" + html.escape(message) + "</div>" if message else ""}
    <div class="controls">
      <div>{filter_text}</div>
      <div>Append <code>?prefix=&lt;cache-key-prefix&gt;&amp;limit=50</code> to filter.</div>
    </div>
    <form method="POST">
      <input type="hidden" name="prefix" value="{html.escape(prefix or "")}">
      <input type="hidden" name="limit" value="{limit}">
      <table>
        <thead>
          <tr>
            <th>Tier</th>
            <th>Summary</th>
            <th>Input</th>
            <th>Lines &amp; Stations</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {rows_html}
        </tbody>
      </table>
      <div class="actions">
        <button type="submit" class="btn danger">Delete selected</button>
      </div>
    </form>
  </div>
</body>
</html>
"""
