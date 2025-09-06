from typing import Any, List, Set
import dspy  # pyright: ignore[reportMissingTypeStubs]
import os
import html
import hashlib
import glob
from datetime import datetime

from dspy.teleprompt.gepa.gepa_utils import DSPyTrace  # pyright: ignore[reportMissingTypeStubs]

from lib.models import AffectedStations, PathStation
from lib.dspy.elevator_affected_area import ElevatorAffectedStationsPredictor
from lib.dspy.llms import LLM

program = ElevatorAffectedStationsPredictor()
unoptimized_program = program

STUDENT_LLM = LLM.GPT_OSS_20B.lm
TEACHER_LLM = LLM.GEMINI_FLASH.lm
TRAINING_N = 40

dspy.settings.configure(lm=STUDENT_LLM)  # pyright: ignore[reportUnknownMemberType]


def create_dspy_example(
    query: str,
    affected_stations: List[PathStation] = [],
) -> dspy.Example:
    if not affected_stations:
        return dspy.Example(query=query, affected_stations=None).with_inputs("query")  # pyright: ignore

    return dspy.Example(  # pyright: ignore
        query=query,
        affected_stations=AffectedStations(affected_stations=affected_stations),
    ).with_inputs("query")


trainset = [
    create_dspy_example(
        "Elevator at Newark Penn Station is out of service. Use the stairs or alternate elevators.",
        affected_stations=[PathStation.NWK],
    ),
    create_dspy_example(
        "At Grove St, the lift/elevator from mezz to platform back in service after earlier outage.",
        affected_stations=[PathStation.GRV],
    ),
    create_dspy_example(
        "At Grove St, the lift/elevator from mezz to platform out of service. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.GRV],
    ),
    create_dspy_example(
        "PATH 33 St elevator temporarily out of service. To NJ: NYC Subway downtown R (and N overnight) to Cortlandt St. To 33 St: PATH to WTC, Subway to 34 Street. Subway fare payment required. Subway elevator status: https://new.mta.info/elevator-escalator-status.",
        affected_stations=[PathStation.S33],
    ),
    create_dspy_example(
        "33 St elevator is back in service after an earlier outage.",
        affected_stations=[PathStation.S33],
    ),
    create_dspy_example(
        "At EXPL, both elevators from street to mezz are back in service after earlier outage.",
        affected_stations=[PathStation.EXP],
    ),
    create_dspy_example(
        "At EXPL, both elevators from street to mezz are out of service due to scheduled cleaning. Please use the escalator entrance.",
        affected_stations=[PathStation.EXP],
    ),
    create_dspy_example(
        "At JSQ, concourse elevator connecting platform with trks 3&4 temporarily out of service. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.JSQ],
    ),
    create_dspy_example(
        "At Newport, elevator from mezz to platform B out of service until further notice. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.NEW],
    ),
    create_dspy_example(
        "At Newport, elevator from mezz to platform B back in service after earlier outage.",
        affected_stations=[PathStation.NEW],
    ),
    create_dspy_example(
        "At Newport, elevator from mezz to platform B out of service. Please use Grove Street or Exchange Place stations if you require elevator service.",
        affected_stations=[PathStation.NEW],
    ),
    create_dspy_example(
        "At Newport, elevator from mezz to platform A out of service until further notice.",
        affected_stations=[PathStation.NEW],
    ),
    create_dspy_example(
        "At Newport, elevator from mezz to platform A back in service after earlier outage.",
        affected_stations=[PathStation.NEW],
    ),
    create_dspy_example(
        "At Grove St, elevator from street to mezz back in service after earlier outage.",
        affected_stations=[PathStation.GRV],
    ),
    create_dspy_example(
        "At Hoboken, elevator from street to fare zone is temporarily out of service.",
        affected_stations=[PathStation.HOB],
    ),
    create_dspy_example(
        "At Grove St, elevator from street to mezz out of service. Please use Newport or Exchange Place stations if you require elevator service.",
        affected_stations=[PathStation.GRV],
    ),
    create_dspy_example(
        "At JSQ, concourse elevator connecting platform with trks 3&4 back in service after earlier outage.",
        affected_stations=[PathStation.JSQ],
    ),
    create_dspy_example(
        "At JSQ, concourse elevator connecting platform with trks 3&4 out of service until further notice. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.JSQ],
    ),
    create_dspy_example(
        "At JSQ, concourse elevator connecting platform with trks 3&4 out of service. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.JSQ],
    ),
    create_dspy_example(
        "At Hoboken, elevator from street to fare zone is out of service. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.HOB],
    ),
    create_dspy_example(
        "At Hoboken, EXPL and Grove, elevator from street to fare zone is out of service. Call 800-234-PATH or use Passenger Assistance Phone if no agent is available.",
        affected_stations=[PathStation.HOB, PathStation.GRV, PathStation.EXP],
    ),
    create_dspy_example(
        "The elevator outage at World Trade Center has been resolved, but outages persist at Christopher Street and 9th Street.",
        affected_stations=[PathStation.CHR, PathStation.S09, PathStation.WTC],
    ),
]


def metric_with_feedback(
    gold: dspy.Example,
    pred: dspy.Prediction,
    trace: DSPyTrace | None = None,
    pred_name: str | None = None,
    pred_trace: DSPyTrace | None = None,
):
    gold_stations: AffectedStations | None = gold.affected_stations  # pyright: ignore
    pred_stations: AffectedStations | None = pred.affected_stations  # pyright: ignore

    # Handle None cases
    if gold_stations is None and pred_stations is None:
        return dspy.Prediction(score=1.0, feedback="Perfect!")

    if gold_stations is None:
        if pred_stations is not None:
            return dspy.Prediction(score=0.0, feedback="Expected none.")

    gold_stations_set: Set[PathStation] = (
        set(gold_stations.affected_stations) if gold_stations else set()  # pyright: ignore
    )
    pred_stations_set: Set[PathStation] = (
        set(pred_stations.affected_stations) if pred_stations else set()  # pyright: ignore
    )

    missing_stations = gold_stations_set - pred_stations_set
    extra_stations = pred_stations_set - gold_stations_set

    intersection_stations = len(gold_stations_set.intersection(pred_stations_set))
    union_stations = len(gold_stations_set.union(pred_stations_set))
    jaccard_score = (
        intersection_stations / union_stations if union_stations > 0 else 1.0
    )

    if jaccard_score == 1.0:
        feedback = "Perfect match of affected stations."
    else:
        feedback_parts: List[str] = []
        if missing_stations:
            feedback_parts.append(f"Missing stations: {', '.join(missing_stations)}")
        if extra_stations:
            feedback_parts.append(f"Extra stations: {', '.join(extra_stations)}")
        feedback = (
            "; ".join(feedback_parts) if feedback_parts else "Some stations mismatch."
        )

    return dspy.Prediction(score=jaccard_score, feedback=feedback)


kwargs: dict[str, Any] = dict(
    num_threads=16,
    display_progress=False,
    display_table=False,
    max_errors=100,
    failure_score=0.99,
)
evaluate = dspy.Evaluate(metric=metric_with_feedback, devset=trainset, **kwargs)


# llms = [
#     LLM.GEMINI_FLASH_LITE,
#     LLM.GEMINI_FLASH,
#     LLM.QWEN3_30B,
#     LLM.QWEN3_235B,
#     LLM.QWEN3_32B,
#     LLM.GPT_OSS_20B,
#     LLM.GPT_OSS_120B,
# ]

# for llm in llms:
#     with dspy.context(lm=llm.lm):
#         print(llm.model)
#         print(evaluate(unoptimized_program).score) # pyright: ignore[reportArgumentType]
#         print()

# exit(0)

unop_results = evaluate(unoptimized_program)
unop_results_list: list[
    tuple[
        float,
        str,
        AffectedStations | None,
        AffectedStations | None,
    ]
] = [
    (
        float(score),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        str(example.query),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        example.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        pred.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    )
    for example, pred, score in unop_results.results  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType, reportUnknownVariableType]
]

print(unop_results_list)

wandb_api_key = os.getenv("WANDB_API_KEY")
teleprompter = dspy.GEPA(
    metric=metric_with_feedback,  # pyright: ignore
    # auto="light",
    num_threads=32,
    reflection_lm=TEACHER_LLM,
    max_full_evals=TRAINING_N,
    use_wandb=wandb_api_key is not None,
    wandb_api_key=wandb_api_key,
)

print("\nStarting COPRO optimization...")
optimized_program: ElevatorAffectedStationsPredictor = teleprompter.compile(  # pyright: ignore
    unoptimized_program,
    trainset=trainset,
)
print("Optimization complete.")
op_results = evaluate(optimized_program)
op_results_list: list[
    tuple[
        float,
        str,
        AffectedStations | None,
        AffectedStations | None,
    ]
] = [
    (
        float(score),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        str(example.query),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        example.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        pred.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    )
    for example, pred, score in op_results.results  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType, reportUnknownVariableType]
]

optimized_program(query="The elevator is out of service.")

optimized_prompt: str = optimized_program.history[-1]["messages"][0]["content"]  # pyright: ignore


# Format the expected, unoptimized and optimized results for display
def format_line_name(line_name: str) -> str:
    if line_name == "JSQ_33_HOB":
        return "JSQ-33rd via HOB"

    formatted_name = line_name.replace("_", "-")
    if formatted_name.endswith("-33"):
        formatted_name += "rd"

    return formatted_name


student_model_name = (
    STUDENT_LLM.model.replace("openrouter/", "").split(":")[0].replace("/", "-")
)
teacher_model_name = (
    TEACHER_LLM.model.replace("openrouter/", "").split(":")[0].replace("/", "-")
)

# Generate SHA1 hash of the optimized prompt
# Use first 8 characters for brevity
prompt_hash = hashlib.sha1(optimized_prompt.encode()).hexdigest()[:8]  # pyright: ignore[reportUnknownMemberType, reportUnknownArgumentType]

# Create filename with student-teacher-hash format
html_filename = (
    f"dspy_elevator_output/{student_model_name}-{teacher_model_name}-{prompt_hash}.html"
)


def format_result(result: AffectedStations | None) -> str:
    if result is None:
        return ""
    elif isinstance(result, AffectedStations):
        sorted_stations = sorted(result.affected_stations, key=lambda s: s.value)
        stations_html = "".join(
            [
                f'<span class="pill station-pill" data-station="{s.name}">{html.escape(s.value)}</span>'
                for s in sorted_stations
            ]
        )
        return f'<div class="pill-container">{stations_html}</div>'


# Generate HTML file comparing results
def generate_results_html(
    unop_score: float,
    op_score: float,
    unop_results_list: list[
        tuple[
            float,
            str,
            AffectedStations | None,
            AffectedStations | None,
        ]
    ],
    op_results_list: list[
        tuple[
            float,
            str,
            AffectedStations | None,
            AffectedStations | None,
        ]
    ],
    optimized_prompt: str,
):
    # Check if models use reasoning
    student_uses_reasoning = "reasoning" in getattr(STUDENT_LLM, "kwargs", {})
    teacher_uses_reasoning = "reasoning" in getattr(TEACHER_LLM, "kwargs", {})

    html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <title>DSPy Elevator Affected Stations Prediction Results</title>
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
        .reasoning-pill {{ display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: bold; background-color: #4CAF50; color: white; }}

        /* Pill styles */
        .pill-container {{ display: flex; flex-wrap: wrap; gap: 5px; align-items: center; padding-bottom: 20px; }}
        .pill {{
            display: inline-block;
            padding: 5px 12px;
            border-radius: 20px;
            font-size: 14px;
            font-weight: 500;
            text-align: center;
            white-space: nowrap;
            vertical-align: middle;
        }}

        /* Score display styles */
        .score-display {{
            position: absolute;
            bottom: 8px;
            right: 12px;
            font-weight: bold;
            font-size: 16px;
        }}
        .score-green-cell {{ background-color: #e8f5e9; color: #2e7d32; }}
        .score-orange-cell {{ background-color: #fff3e0; color: #ef6c00; }}
        .score-red-cell {{ background-color: #ffebee; color: #c62828; }}

        .score-green-text {{ color: #2e7d32; }}
        .score-orange-text {{ color: #ef6c00; }}
        .score-red-text {{ color: #c62828; }}

        /* Light pastel colors for stations - based on station name */
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

        /* Dark pastel colors for lines - based on line name */
        .line-pill[data-line="NWK_WTC"] {{ background-color: #e57373; color: #FFFFFF; border: 1px solid #d32f2f; }}
        .line-pill[data-line="JSQ_WTC"] {{ background-color: #26c6da; color: #FFFFFF; border: 1px solid #00acc1; }} /* No official color provided */
        .line-pill[data-line="HOB_WTC"] {{ background-color: #81c784; color: #FFFFFF; border: 1px solid #388e3c; }}
        .line-pill[data-line="JSQ_33"] {{ background-color: #fff176; color: #5d4037; border: 1px solid #fdd835; }}
        .line-pill[data-line="HOB_33"] {{ background-color: #64b5f6; color: #FFFFFF; border: 1px solid #1976d2; }}
        .line-pill[data-line="JSQ_33_HOB"] {{ background-color: #b2dfdb; color: #00796b; border: 1px solid #80cbc4; }}
    </style>
</head>
<body>
    <h1>DSPy Elevator Affected Stations Prediction Results</h1>

    <div class="model-info">
        <p><strong>Student Model:</strong> {student_model_name} {'<span class="reasoning-pill">Reasoning</span>' if student_uses_reasoning else ""}</p>
        <p><strong>Teacher Model:</strong> {teacher_model_name} {'<span class="reasoning-pill">Reasoning</span>' if teacher_uses_reasoning else ""}</p>
        <p><strong>Training Iterations:</strong> {TRAINING_N}</p>
    </div>


    <div class="model-info">
        <p>Unoptimized Score: <span class="score {get_score_class(unop_score / 100)}-text">{int(unop_score)}%</span></p>
        <p>Optimized Score: <span class="score {get_score_class(op_score / 100)}-text">{int(op_score)}%</span></p>
    </div>

    <h2>Detailed Results Comparison (Elevator Affected Stations)</h2>
    <table>
        <tr>
            <th>Query</th>
            <th>Expected</th>
            <th>Optimized</th>
            <th>Unoptimized</th>
        </tr>
"""

    # Combine results for comparison
    for i, (
        unop_score,
        query,
        expected_stations,
        unop_pred_stations,
    ) in enumerate(unop_results_list):
        if i < len(op_results_list):
            op_score, _, _, op_pred_stations = op_results_list[i]

            html_content += f"""
        <tr>
            <td>{html.escape(query)}</td>
            <td>
            {format_result(expected_stations)}
            </td>
            <td class="{get_score_class(op_score)}-cell">
            {format_result(op_pred_stations)}
                <div class="score-display">{op_score:.0%}</div>
            </td>
            <td class="{get_score_class(unop_score)}-cell">
                {format_result(unop_pred_stations)}
                <div class="score-display">{unop_score:.0%}</div>
            </td>
        </tr>
"""

    html_content += f"""
    </table>

    <h2>Optimized Prompt</h2>
    <pre><code>{html.escape(optimized_prompt)}</code></pre>
</body>
</html>
"""

    os.makedirs("dspy_elevator_output", exist_ok=True)

    with open(html_filename, "w") as f:
        f.write(html_content)

    print(f"Results comparison saved to {html_filename}")

    # Generate index.html with all HTML files
    generate_html_index()


# Helper function to determine score pill class
def get_score_class(score: float) -> str:
    if score == 1.0:
        return "score-green"
    elif 0.5 <= score < 1.0:
        return "score-orange"
    else:
        return "score-red"


def generate_html_index():
    """Generate an index.html file listing all HTML files sorted by modification date."""

    # Get all HTML files in the current directory except index.html itself
    html_files: list[tuple[str, datetime]] = []
    for file in glob.glob("dspy_elevator_output/*.html"):
        if not file.endswith("index.html") and file.endswith(".html"):
            stat = os.stat(file)
            mod_time = datetime.fromtimestamp(stat.st_mtime)
            html_files.append((file, mod_time))

    # Sort by modification time (newest first)
    html_files.sort(key=lambda x: x[1], reverse=True)

    # Generate HTML content
    html_content = """<!DOCTYPE html>
<html>
<head>
    <title>Elevator Output Files Index</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        h1 { color: #333; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
        th { background-color: #f2f2f2; }
        tr:nth-child(even) { background-color: #f9f9f9; }
        a { text-decoration: none; color: #0066cc; }
        a:hover { text-decoration: underline; }
        .score { font-weight: bold; }
        .score-green-text { color: #2e7d32; }
        .score-orange-text { color: #ef6c00; }
        .score-red-text { color: #c62828; }
    </style>
</head>
<body>
    <h1>Generated Elevator HTML Files</h1>
    <table>
        <tr>
            <th>File Name</th>
            <th>Last Modified</th>
            <th>Optimized Score</th>
        </tr>
"""

    for file, mod_time in html_files:
        # Extract optimized score from the HTML file
        optimized_score = "N/A"
        try:
            with open(file, "r") as f:
                content = f.read()
                # Look for the optimized score in the model-info div
                import re

                score_match = re.search(
                    r'Optimized Score: <span class="score ([^"]+)">(\d+)%</span>',
                    content,
                )
                if score_match:
                    score_class = score_match.group(1)
                    score_value = score_match.group(2)
                    optimized_score = (
                        f'<span class="score {score_class}">{score_value}%</span>'
                    )
                else:
                    optimized_score = "N/A"
        except Exception:
            optimized_score = "N/A"

        file_link = file.replace("dspy_elevator_output/", "")
        html_content += f"""
        <tr>
            <td><a href="{file_link}">{file_link}</a></td>
            <td>{mod_time.strftime("%Y-%m-%d %H:%M:%S")}</td>
            <td>{optimized_score}</td>
        </tr>
"""

    html_content += """
    </table>
</body>
</html>
"""

    # Write index.html
    with open("dspy_elevator_output/index.html", "w") as f:
        f.write(html_content)

    print("Index file generated: dspy_elevator_output/index.html")


# Generate the HTML file
generate_results_html(
    unop_score=unop_results.score,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    op_score=op_results.score,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    unop_results_list=unop_results_list,
    op_results_list=op_results_list,
    optimized_prompt=optimized_prompt,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
)
