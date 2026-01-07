from __future__ import annotations
# ruff: noqa: E402

import argparse
import hashlib
import html
import os
import sys
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import datetime
from itertools import zip_longest
from pathlib import Path
from typing import Callable, Protocol, Sequence, cast

# Ensure project src/ is on the path when invoked from repo root.
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

OUTPUT_ROOT = ROOT / "build" / "gepa_runs"

import dspy

from src.llm.responses.gepa.simple_text_gepa import (
    PreprocessProgram,
    RemoveSingleAreaProgram,
    build_default_preprocess_trainset,
    build_default_preprocess_valset,
    build_default_remove_single_area_trainset,
    build_default_remove_single_area_valset,
    metric_preprocess,
    metric_remove_single_area,
    optimize_preprocess,
    optimize_remove_single_area,
)


@dataclass
class TextEvaluationRow:
    score: float
    inputs: list[tuple[str, str]]
    set_name: str | None
    expected: str
    pred: str
    feedback: str


class _ScoreLike(Protocol):
    feedback: str | None

    def __float__(self) -> float: ...


@dataclass(frozen=True)
class OptimizationTargetConfig:
    name: str
    program_factory: Callable[[dspy.LM | None], dspy.Module]
    optimize_fn: Callable[..., dspy.Module]
    trainset_builder: Callable[[], list[dspy.Example]]
    valset_builder: Callable[[], list[dspy.Example]]
    metric_fn: Callable[[dspy.Example, dspy.Prediction], dspy.Prediction]
    input_fields: tuple[str, ...]
    output_field: str
    html_title: str


TARGET_CONFIGS: dict[str, OptimizationTargetConfig] = {
    "preprocess": OptimizationTargetConfig(
        name="preprocess",
        program_factory=lambda lm: PreprocessProgram(lm=lm),
        optimize_fn=optimize_preprocess,
        trainset_builder=build_default_preprocess_trainset,
        valset_builder=build_default_preprocess_valset,
        metric_fn=metric_preprocess,
        input_fields=("alert",),
        output_field="cleaned_text",
        html_title="DSPy Preprocess Prompt Results Comparison",
    ),
    "remove_single_area": OptimizationTargetConfig(
        name="remove_single_area",
        program_factory=lambda lm: RemoveSingleAreaProgram(lm=lm),
        optimize_fn=optimize_remove_single_area,
        trainset_builder=build_default_remove_single_area_trainset,
        valset_builder=build_default_remove_single_area_valset,
        metric_fn=metric_remove_single_area,
        input_fields=("text", "single_area"),
        output_field="updated_text",
        html_title="DSPy Remove Single Area Prompt Results Comparison",
    ),
}


def _sanitize_model_name(model_id: str | None) -> str:
    if not model_id:
        return "none"
    return model_id.replace("openrouter/", "").split(":")[0].replace("/", "-")


def _create_run_dir(
    student_name: str, teacher_name: str | None, *, target: str
) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    sanitized_student = _sanitize_model_name(student_name)
    sanitized_teacher = _sanitize_model_name(teacher_name)
    suffix = f"-{sanitized_teacher}" if sanitized_teacher != "none" else ""
    target_slug = target.replace("_", "-")
    run_dir = OUTPUT_ROOT / f"{target_slug}-{timestamp}-{sanitized_student}{suffix}"
    run_dir.mkdir(parents=True, exist_ok=True)
    return run_dir


def _example_key(example: dspy.Example, fields: Sequence[str]) -> str:
    return "||".join(cast(str, getattr(example, f, "")) for f in fields)


def _extract_rows(
    eval_result: dspy.Prediction,
    *,
    config: OptimizationTargetConfig,
    dataset_lookup: Mapping[str, str] | None = None,
) -> list[TextEvaluationRow]:
    rows: list[TextEvaluationRow] = []
    results = cast(
        Sequence[tuple[dspy.Example, dspy.Prediction, _ScoreLike | float]],
        getattr(eval_result, "results", ()),
    )

    for example, prediction, score in results:
        score_value = float(score) if hasattr(score, "__float__") else 0.0
        feedback = cast(str, getattr(score, "feedback", "") or "")
        key = _example_key(example, config.input_fields)
        set_name = dataset_lookup.get(key) if dataset_lookup else None
        inputs_display = [
            (field, cast(str, getattr(example, field, ""))) for field in config.input_fields
        ]
        expected = cast(str, getattr(example, config.output_field, ""))
        pred_val = cast(str, getattr(prediction, config.output_field, ""))
        rows.append(
            TextEvaluationRow(
                score=score_value,
                inputs=inputs_display,
                set_name=set_name,
                expected=expected,
                pred=pred_val,
                feedback=feedback,
            )
        )
    return rows


def _generate_results_html(
    *,
    run_dir: Path,
    student_model: str,
    teacher_model: str | None,
    max_full_evals: int,
    optimized_prompt: str,
    unoptimized_eval: dspy.Prediction,
    optimized_eval: dspy.Prediction,
    config: OptimizationTargetConfig,
    dataset_lookup: Mapping[str, str] | None = None,
) -> Path:
    student_model_name = _sanitize_model_name(student_model)
    teacher_model_name = _sanitize_model_name(teacher_model)
    target_slug = config.name.replace("_", "-")

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

    unop_rows = _extract_rows(
        unoptimized_eval, config=config, dataset_lookup=dataset_lookup
    )
    op_rows = _extract_rows(
        optimized_eval, config=config, dataset_lookup=dataset_lookup
    )

    rows_html_parts: list[str] = []
    for unop_row, op_row in zip_longest(unop_rows, op_rows, fillvalue=None):
        source_row = unop_row or op_row
        if source_row is None:
            continue

        op_score = op_row.score if op_row else 0.0
        unop_score = unop_row.score if unop_row else 0.0
        op_feedback = op_row.feedback if op_row else ""
        unop_feedback = unop_row.feedback if unop_row else ""

        inputs_html = "".join(
            f"<div><strong>{html.escape(name)}:</strong> {html.escape(value)}</div>"
            for name, value in source_row.inputs
        )

        rows_html_parts.append(
            f"""
        <tr>
            <td>
                {inputs_html}
                {f'<span class="pill dataset-pill {source_row.set_name}">{source_row.set_name.title()}</span>' if source_row.set_name else ""}
            </td>
            <td>{html.escape(source_row.expected)}</td>
            <td class="{ 'score-green-cell' if op_score == 1.0 else 'score-orange-cell' if op_score >= 0.5 else 'score-red-cell'}">
                {html.escape(op_row.pred if op_row else '')}
                {'<div class="feedback">' + html.escape(op_feedback) + "</div>" if op_feedback else ""}
                <div class="score-display">{op_score:.0%}</div>
            </td>
            <td class="{ 'score-green-cell' if unop_score == 1.0 else 'score-orange-cell' if unop_score >= 0.5 else 'score-red-cell'}">
                {html.escape(unop_row.pred if unop_row else '')}
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
    <title>{html.escape(config.html_title)}</title>
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
    </style>
</head>
<body>
    <h1>{html.escape(config.html_title)}</h1>

    <div class="model-info">
        <p><strong>Student Model:</strong> {html.escape(student_model)}</p>
        <p><strong>Teacher Model:</strong> {html.escape(teacher_model or "None")}</p>
        <p><strong>Target:</strong> {html.escape(config.name)}</p>
        <p><strong>Training Iterations (max_full_evals):</strong> {max_full_evals}</p>
        <p><strong>Run Directory:</strong> {html.escape(str(run_dir.relative_to(ROOT)))} </p>
        <p><strong>Created At:</strong> {created_at}</p>
    </div>

    <div class="model-info">
        <p>Unoptimized Score: <span class="score {'score-green-text' if unop_score_percent == 100 else 'score-orange-text' if unop_score_percent >= 50 else 'score-red-text'}">{int(unop_score_percent)}%</span></p>
        <p>Optimized Score: <span class="score {'score-green-text' if op_score_percent == 100 else 'score-orange-text' if op_score_percent >= 50 else 'score-red-text'}">{int(op_score_percent)}%</span></p>
    </div>

    <h2>Detailed Results Comparison</h2>
    <table>
        <tr>
            <th>Inputs</th>
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
        description="Run GEPA optimization for simple text prompts (preprocess/remove_single_area)."
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
    _ = parser.add_argument(
        "--target",
        choices=sorted(TARGET_CONFIGS.keys()),
        default="preprocess",
        help="Optimization target to run.",
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

    dataset_lookup: dict[str, str] = {
        _example_key(ex, target_config.input_fields): "train" for ex in trainset
    }
    dataset_lookup.update(
        {
            _example_key(ex, target_config.input_fields): "val"
            for ex in valset
        }
    )

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
        student_lm.model, teacher_lm.model if teacher_lm else None, target=target
    )
    html_path = _generate_results_html(
        run_dir=run_dir,
        student_model=student_lm.model,
        teacher_model=teacher_lm.model if teacher_lm else None,
        max_full_evals=max_full_evals,
        optimized_prompt=optimized_prompt,
        unoptimized_eval=unoptimized_eval,
        optimized_eval=optimized_eval,
        config=target_config,
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
