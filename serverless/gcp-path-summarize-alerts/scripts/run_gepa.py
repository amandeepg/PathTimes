from __future__ import annotations
# ruff: noqa: E402

import argparse
import hashlib
import html
import os
import sys
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime
from itertools import zip_longest
from pathlib import Path
from typing import Protocol, Callable, cast

# Ensure project src/ is on the path when invoked from repo root.
ROOT = Path(__file__).resolve().parents[1]
# Put repo root on path so imports use the "src" package (e.g., src.models, src.llm.*).
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

OUTPUT_ROOT = ROOT / "build" / "gepa_runs"

import dspy

from src.models import AffectedLines, AffectedStations, PathLine, PathStation
from src.llm.responses.gepa.affected_areas_gepa import (
    AffectedAreasProgram,
    build_default_affected_areas_trainset,
    build_default_affected_areas_valset,
    metric_affected_areas,
    optimize_affected_areas,
)
from src.llm.responses.gepa.elevator_affected_areas_gepa import (
    ElevatorAffectedAreasProgram,
    build_default_elevator_affected_areas_trainset,
    build_default_elevator_affected_areas_valset,
    metric_elevator_affected_areas,
    optimize_elevator_affected_areas,
)


@dataclass
class EvaluationRow:
    score: float
    alert: str
    set_name: str | None
    expected_stations: AffectedStations | None
    expected_lines: AffectedLines | None
    pred_stations: AffectedStations | None
    pred_lines: AffectedLines | None
    feedback: str


@dataclass(frozen=True)
class OptimizationTargetConfig:
    name: str
    program_factory: Callable[[dspy.LM | None], dspy.Module]
    optimize_fn: Callable[..., dspy.Module]
    trainset_builder: Callable[[], list[dspy.Example]]
    valset_builder: Callable[[], list[dspy.Example]]
    metric_fn: Callable[[dspy.Example, dspy.Prediction], dspy.Prediction]
    html_title: str


TARGET_CONFIGS: dict[str, OptimizationTargetConfig] = {
    "area": OptimizationTargetConfig(
        name="area",
        program_factory=lambda lm: AffectedAreasProgram(lm=lm),
        optimize_fn=optimize_affected_areas,
        trainset_builder=build_default_affected_areas_trainset,
        valset_builder=build_default_affected_areas_valset,
        metric_fn=metric_affected_areas,
        html_title="DSPy Affected Area Prediction Results Comparison",
    ),
    "elevator_area": OptimizationTargetConfig(
        name="elevator_area",
        program_factory=lambda lm: ElevatorAffectedAreasProgram(lm=lm),
        optimize_fn=optimize_elevator_affected_areas,
        trainset_builder=build_default_elevator_affected_areas_trainset,
        valset_builder=build_default_elevator_affected_areas_valset,
        metric_fn=metric_elevator_affected_areas,
        html_title="DSPy Elevator Affected Area Prediction Results Comparison",
    ),
}


def _sanitize_model_name(model_id: str | None) -> str:
    if not model_id:
        return "none"
    return model_id.replace("openrouter/", "").split(":")[0].replace("/", "-")


def format_line_name(line_name: str) -> str:
    if line_name == "JSQ_33_HOB":
        return "JSQ-33rd via HOB"

    formatted_name = line_name.replace("_", "-")
    if formatted_name.endswith("-33"):
        formatted_name += "rd"

    return formatted_name


def get_score_class(score: float) -> str:
    if score == 1.0:
        return "score-green"
    if 0.5 <= score < 1.0:
        return "score-orange"
    return "score-red"


def format_result(result: AffectedStations | AffectedLines | None) -> str:
    if result is None:
        return ""
    stations_raw = cast(
        Sequence[PathStation] | None, getattr(result, "affected_stations", None)
    )
    if stations_raw is not None:
        sorted_stations = sorted(stations_raw, key=lambda station: station.value)
        stations_html = "".join(
            f'<span class="pill station-pill" data-station="{s.name}">{html.escape(s.value)}</span>'
            for s in sorted_stations
        )
        return f'<div class="pill-container">{stations_html}</div>'
    lines_raw = cast(Sequence[PathLine] | None, getattr(result, "affected_lines", None))
    if lines_raw is not None:
        sorted_lines = sorted(lines_raw, key=lambda line: line.value)
        lines_html = "".join(
            f'<span class="pill line-pill" data-line="{line.name}">{html.escape(format_line_name(line.value))}</span>'
            for line in sorted_lines
        )
        return f'<div class="pill-container">{lines_html}</div>'
    return ""


class _ScoreLike(Protocol):
    feedback: str | None

    def __float__(self) -> float: ...


def _extract_rows(
    eval_result: dspy.Prediction, *, dataset_lookup: Mapping[str, str] | None = None
) -> list[EvaluationRow]:
    rows: list[EvaluationRow] = []
    results = cast(
        Sequence[tuple[dspy.Example, dspy.Prediction, _ScoreLike | float]],
        getattr(eval_result, "results", ()),
    )

    for example, prediction, score in results:
        score_value = float(score) if hasattr(score, "__float__") else 0.0
        feedback = cast(str, getattr(score, "feedback", "") or "")
        alert_text = cast(str, getattr(example, "alert", ""))
        set_name = dataset_lookup.get(alert_text) if dataset_lookup else None
        rows.append(
            EvaluationRow(
                score=score_value,
                alert=alert_text,
                set_name=set_name,
                expected_stations=cast(
                    AffectedStations | None, getattr(example, "affected_stations", None)
                ),
                expected_lines=cast(
                    AffectedLines | None, getattr(example, "affected_lines", None)
                ),
                pred_stations=cast(
                    AffectedStations | None,
                    getattr(prediction, "affected_stations", None),
                ),
                pred_lines=cast(
                    AffectedLines | None, getattr(prediction, "affected_lines", None)
                ),
                feedback=feedback,
            )
        )
    return rows


def _create_run_dir(
    student_name: str, teacher_name: str | None, *, target: str
) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    sanitized_student = _sanitize_model_name(student_name)
    sanitized_teacher = _sanitize_model_name(teacher_name)
    suffix = f"-{sanitized_teacher}" if sanitized_teacher != "none" else ""
    target_slug = target.replace("_", "-")
    prefix = f"{target_slug}-" if target_slug else ""
    run_dir = OUTPUT_ROOT / f"{prefix}{timestamp}-{sanitized_student}{suffix}"
    run_dir.mkdir(parents=True, exist_ok=True)
    return run_dir


def _generate_results_html(
    *,
    run_dir: Path,
    student_model: str,
    teacher_model: str | None,
    max_full_evals: int,
    optimized_prompt: str,
    unoptimized_eval: dspy.Prediction,
    optimized_eval: dspy.Prediction,
    html_title: str,
    target: str,
    dataset_lookup: Mapping[str, str] | None = None,
) -> Path:
    student_model_name = _sanitize_model_name(student_model)
    teacher_model_name = _sanitize_model_name(teacher_model)
    target_slug = target.replace("_", "-")

    prompt_hash = (
        hashlib.sha1(optimized_prompt.encode()).hexdigest()[:8]
        if optimized_prompt
        else "no-prompt"
    )
    html_path = (
        run_dir
        / f"{target_slug}-{student_model_name}-{teacher_model_name}-{prompt_hash}.html"
    )

    unop_score_percent = float(getattr(unoptimized_eval, "score", 0.0))
    op_score_percent = float(getattr(optimized_eval, "score", 0.0))

    unop_rows = _extract_rows(unoptimized_eval, dataset_lookup=dataset_lookup)
    op_rows = _extract_rows(optimized_eval, dataset_lookup=dataset_lookup)

    rows_html_parts: list[str] = []
    for unop_row, op_row in zip_longest(unop_rows, op_rows, fillvalue=None):
        source_row = unop_row or op_row
        if source_row is None:
            continue

        op_score = op_row.score if op_row else 0.0
        unop_score = unop_row.score if unop_row else 0.0

        op_feedback = op_row.feedback if op_row else ""
        unop_feedback = unop_row.feedback if unop_row else ""

        rows_html_parts.append(
            f"""
        <tr>
            <td>
                {html.escape(source_row.alert)}
                {f'<span class="pill dataset-pill {source_row.set_name}">{source_row.set_name.title()}</span>' if source_row.set_name else ""}
            </td>
            <td>
                {format_result(source_row.expected_stations)}
                {format_result(source_row.expected_lines)}
            </td>
            <td class="{get_score_class(op_score)}-cell">
                {format_result(op_row.pred_stations if op_row else None)}
                {format_result(op_row.pred_lines if op_row else None)}
                {'<div class="feedback">' + html.escape(op_feedback) + "</div>" if op_feedback else ""}
                <div class="score-display">{op_score:.0%}</div>
            </td>
            <td class="{get_score_class(unop_score)}-cell">
                {format_result(unop_row.pred_stations if unop_row else None)}
                {format_result(unop_row.pred_lines if unop_row else None)}
                {'<div class="feedback">' + html.escape(unop_feedback) + "</div>" if unop_feedback else ""}
                <div class="score-display">{unop_score:.0%}</div>
            </td>
        </tr>
        """
        )

    created_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset=\"utf-8\" />
    <meta name=\"optimized-score\" content=\"{op_score_percent:.2f}\" />
    <meta name=\"unoptimized-score\" content=\"{unop_score_percent:.2f}\" />
    <title>{html.escape(html_title)}</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; }}
        h1, h2 {{ color: #333; }}
        pre {{ background-color: #f5f5f5; padding: 15px; border-radius: 5px; white-space: pre-wrap; word-wrap: break-word; }}
        code {{ font-family: 'Courier New', Courier, monospace; }}
        table {{ border-collapse: collapse; width: 100%; margin-bottom: 20px; }}
        th, td {{ border: 1px solid #ddd; padding: 12px; text-align: left; vertical-align: top; position: relative; }}
        th {{ background-color: #f2f2f2; }}
        tr:nth-child(even) {{ background-color: #f9f9f9; }}
        .score {{ font-weight: bold; }}
        .model-info {{ background-color: #f0f8ff; padding: 15px; border-radius: 5px; margin-bottom: 20px; }}
        .model-info h3 {{ margin-top: 0; }}
        .pill-container {{ display: flex; flex-wrap: wrap; gap: 5px; align-items: center; padding-bottom: 10px; }}
        .pill {{ display: inline-block; padding: 5px 12px; border-radius: 20px; font-size: 14px; font-weight: 500; text-align: center; white-space: nowrap; vertical-align: middle; }}
        .score-display {{ position: absolute; bottom: 8px; right: 12px; font-weight: bold; font-size: 16px; }}
        .score-green-cell {{ background-color: #e8f5e9; color: #2e7d32; }}
        .score-orange-cell {{ background-color: #fff3e0; color: #ef6c00; }}
        .score-red-cell {{ background-color: #ffebee; color: #c62828; }}
        .score-green-text {{ color: #2e7d32; }}
        .score-orange-text {{ color: #ef6c00; }}
        .score-red-text {{ color: #c62828; }}
        .feedback {{ color: #555; font-size: 12px; margin-top: 6px; }}

        /* Dataset pills */
        .dataset-pill {{ margin-left: 8px; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; color: #fff; }}
        .dataset-pill.train {{ background-color: #1976d2; }}
        .dataset-pill.val {{ background-color: #9c27b0; }}

        /* Station pills */
        .station-pill[data-station="NWK"] {{ background-color: #e1f5fe; color: #01579b; border: 1px solid #81d4fa; }}
        .station-pill[data-station="HAR"] {{ background-color: #e8f5e9; color: #1b5e20; border: 1px solid #a5d6a7; }}
        .station-pill[data-station="JSQ"] {{ background-color: #fff3e0; color: #e65100; border: 1px solid #ffcc80; }}
        .station-pill[data-station="GRV"] {{ background-color: #fce4ec; color: #880e4f; border: 1px solid #f48fb1; }}
        .station-pill[data-station="EXP"] {{ background-color: #f3e5f5; color: #4a148c; border: 1px solid #ce93d8; }}
        .station-pill[data-station="WTC"] {{ background-color: #e0f2f1; color: #004d40; border: 1px solid #80cbc4; }}
        .station-pill[data-station="HOB"] {{ background-color: #fff8e1; color: #ff6f00; border: 1px solid #ffe082; }}
        .station-pill[data-station="NEW"] {{ background-color: #e8eaf6; color: #1a237e; border: 1px solid #9fa8da; }}
        .station-pill[data-station="CHR"] {{ background-color: #efebe9; color: #3e2723; border: 1px solid #bcaaa4; }}
        .station-pill[data-station="S09"] {{ background-color: #f1f8e9; color: #33691e; border: 1px solid #aed581; }}
        .station-pill[data-station="S14"] {{ background-color: #e0f7fa; color: #006064; border: 1px solid #80deea; }}
        .station-pill[data-station="S23"] {{ background-color: #fbe9e7; color: #bf360c; border: 1px solid #ffab91; }}
        .station-pill[data-station="S33"] {{ background-color: #f9fbe7; color: #827717; border: 1px solid #dce775; }}

        /* Line pills */
        .line-pill[data-line="NWK_WTC"] {{ background-color: #e57373; color: #ffffff; border: 1px solid #d32f2f; }}
        .line-pill[data-line="JSQ_WTC"] {{ background-color: #26c6da; color: #ffffff; border: 1px solid #00acc1; }}
        .line-pill[data-line="HOB_WTC"] {{ background-color: #81c784; color: #ffffff; border: 1px solid #388e3c; }}
        .line-pill[data-line="JSQ_33"] {{ background-color: #fff176; color: #5d4037; border: 1px solid #fdd835; }}
        .line-pill[data-line="HOB_33"] {{ background-color: #64b5f6; color: #ffffff; border: 1px solid #1976d2; }}
        .line-pill[data-line="JSQ_33_HOB"] {{ background-color: #b2dfdb; color: #00796b; border: 1px solid #80cbc4; }}
    </style>
</head>
<body>
    <h1>{html.escape(html_title)}</h1>

    <div class="model-info">
        <p><strong>Student Model:</strong> {html.escape(student_model)}</p>
        <p><strong>Teacher Model:</strong> {html.escape(teacher_model or "None")}</p>
        <p><strong>Target:</strong> {html.escape(target)}</p>
        <p><strong>Training Iterations (max_full_evals):</strong> {max_full_evals}</p>
        <p><strong>Run Directory:</strong> {html.escape(str(run_dir.relative_to(ROOT)))} </p>
        <p><strong>Created At:</strong> {created_at}</p>
    </div>

    <div class="model-info">
        <p>Unoptimized Score: <span class="score {get_score_class(unop_score_percent / 100)}-text">{int(unop_score_percent)}%</span></p>
        <p>Optimized Score: <span class="score {get_score_class(op_score_percent / 100)}-text">{int(op_score_percent)}%</span></p>
    </div>

    <h2>Detailed Results Comparison</h2>
    <table>
        <tr>
            <th>Query</th>
            <th>Expected</th>
            <th>Optimized</th>
            <th>Unoptimized</th>
        </tr>
        {"".join(rows_html_parts)}
    </table>

    <h2>Optimized Prompt</h2>
    <pre><code>{html.escape(optimized_prompt)}</code></pre>
</body>
</html>
"""

    run_dir.mkdir(parents=True, exist_ok=True)
    _ = html_path.write_text(html_content)
    return html_path


def _generate_runs_index(base_dir: Path) -> None:
    html_files: list[Path] = [
        p for p in base_dir.glob("*/*.html") if p.name != "index.html"
    ]
    if not html_files:
        return

    rows: list[tuple[str, str, float, str]] = []
    for file in html_files:
        stat = file.stat()
        optimized_score = "N/A"
        try:
            content = file.read_text()
            start = content.find('meta name="optimized-score"')
            if start != -1:
                marker = 'content="'
                idx = content.find(marker, start)
                end = content.find('"', idx + len(marker))
                if idx != -1 and end != -1:
                    optimized_score = content[idx + len(marker) : end]
        except OSError:
            optimized_score = "N/A"

        rows.append(
            (
                file.parent.name,
                file.name,
                stat.st_mtime,
                optimized_score,
            )
        )

    rows.sort(key=lambda x: x[2], reverse=True)

    table_rows = "".join(
        f"""
        <tr>
            <td><a href=\"{html.escape(run)}/{html.escape(filename)}\">{html.escape(run)}/{html.escape(filename)}</a></td>
            <td>{datetime.fromtimestamp(mod_time).strftime("%Y-%m-%d %H:%M:%S")}</td>
            <td>{html.escape(score)}</td>
        </tr>
        """
        for run, filename, mod_time, score in rows
    )

    index_html = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset=\"utf-8\" />
    <title>GEPA Runs Index</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; }}
        table {{ border-collapse: collapse; width: 100%; }}
        th, td {{ border: 1px solid #ddd; padding: 12px; text-align: left; }}
        th {{ background-color: #f2f2f2; }}
        tr:nth-child(even) {{ background-color: #f9f9f9; }}
    </style>
</head>
<body>
    <h1>GEPA Run Artifacts</h1>
    <table>
        <tr>
            <th>Run</th>
            <th>Last Modified</th>
            <th>Optimized Score (%)</th>
        </tr>
        {table_rows}
    </table>
</body>
</html>
"""

    base_dir.mkdir(parents=True, exist_ok=True)
    _ = (base_dir / "index.html").write_text(index_html)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run GEPA optimization for affected areas prompts."
    )
    _ = parser.add_argument(
        "--student-model",
        default="openai/gpt-5-nano",
        help="Model id for the student LM (default: openai/gpt-5-nano)",
    )
    _ = parser.add_argument(
        "--teacher-model",
        default="openai/gpt-5-mini",
        help="Model id for the teacher LM (default: openai/gpt-5-mini).",
    )
    _ = parser.add_argument(
        "--target",
        choices=sorted(TARGET_CONFIGS.keys()),
        default="area",
        help="Optimization target: 'area' for line/station detection or 'elevator_area' for elevator-only alerts.",
    )
    _ = parser.add_argument(
        "--max-full-evals",
        type=int,
        default=20,
        help="Maximum full evaluations during GEPA (default: 20).",
    )
    _ = parser.add_argument(
        "--threads",
        type=int,
        default=8,
        help="Number of threads for GEPA/evaluation (default: 8).",
    )
    args = parser.parse_args()

    if not os.getenv("OPENAI_API_KEY"):
        parser.error("OPENAI_API_KEY must be set in the environment to run GEPA.")

    student_model: str = cast(str, args.student_model)
    teacher_model: str = cast(str, args.teacher_model)
    max_full_evals: int = cast(int, args.max_full_evals)
    threads: int = cast(int, args.threads)
    target: str = cast(str, args.target)

    target_config = TARGET_CONFIGS[target]

    # Avoid ADC lookup for local runs by defaulting PROJECT_ID if unset.
    _ = os.environ.setdefault("PROJECT_ID", "local-dev")

    def build_lm(model_id: str) -> dspy.LM:
        is_gpt5 = "gpt-5" in model_id
        if is_gpt5:
            return dspy.LM(model_id, temperature=1.0, max_tokens=50000)
        return dspy.LM(model_id)

    student_lm = build_lm(student_model)
    teacher_lm = build_lm(teacher_model) if teacher_model else None

    dspy.settings.configure(lm=student_lm)

    trainset = target_config.trainset_builder()
    valset = target_config.valset_builder()

    dataset_lookup: dict[str, str] = {cast(str, ex.alert): "train" for ex in trainset}
    dataset_lookup.update({cast(str, ex.alert): "val" for ex in valset})

    optimized_program = target_config.optimize_fn(
        student_lm=student_lm,
        teacher_lm=teacher_lm,
        max_full_evals=max_full_evals,
        num_threads=threads,
    )

    combined_devset = [*trainset, *valset]
    evaluator = dspy.Evaluate(
        metric=target_config.metric_fn,
        devset=combined_devset,
        num_threads=threads,
        display_progress=False,
        display_table=False,
    )

    optimized_eval: dspy.Prediction

    unoptimized_program = target_config.program_factory(student_lm)
    unoptimized_eval = cast(dspy.Prediction, evaluator(unoptimized_program))
    optimized_eval = cast(dspy.Prediction, evaluator(optimized_program))

    optimized_prompt = (
        cast(
            str | None,
            getattr(
                getattr(getattr(optimized_program, "predict", None), "signature", None),
                "instructions",
                None,
            ),
        )
        or ""
    )

    run_dir = _create_run_dir(
        student_lm.model,
        teacher_lm.model if teacher_lm else None,
        target=target_config.name,
    )
    html_path = _generate_results_html(
        run_dir=run_dir,
        student_model=student_lm.model,
        teacher_model=teacher_lm.model if teacher_lm else None,
        max_full_evals=max_full_evals,
        optimized_prompt=optimized_prompt,
        unoptimized_eval=unoptimized_eval,
        optimized_eval=optimized_eval,
        html_title=target_config.html_title,
        target=target_config.name,
        dataset_lookup=dataset_lookup,
    )

    _generate_runs_index(OUTPUT_ROOT)

    print(f"Optimization complete for target={target_config.name}.")
    print("Unoptimized score:", getattr(unoptimized_eval, "score", "n/a"))
    print("Optimized score:", getattr(optimized_eval, "score", "n/a"))
    if optimized_prompt:
        print("\nOptimized prompt:\n")
        print(optimized_prompt)
    print(f"HTML report saved to {html_path.relative_to(ROOT)}")
    print(f"Run directory: {run_dir.relative_to(ROOT)}")
    print(f"Runs index: {(OUTPUT_ROOT / 'index.html').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
