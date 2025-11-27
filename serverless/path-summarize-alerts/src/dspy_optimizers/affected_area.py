import sys

sys.path.append("src")

from typing import Any, List, Set
import dspy
import os
import html
import hashlib
import glob
from datetime import datetime

from dspy.teleprompt.gepa.gepa_utils import DSPyTrace

from lib.models import AffectedLines, AffectedStations, PathLine, PathStation
from lib.llm_programs.affected_area import AffectedAreaPredictor
from lib.llm_programs.llms import LLM

program = AffectedAreaPredictor()
unoptimized_program = program

STUDENT_LLM = LLM.QWEN3_32B.lm
TEACHER_LLM = LLM.GEMINI_FLASH.lm
TRAINING_N = 40

dspy.settings.configure(lm=STUDENT_LLM)


def create_dspy_example(
    query: str,
    affected_stations: List[PathStation] = [],
    affected_lines: List[PathLine] = [],
) -> dspy.Example:
    if not affected_stations and not affected_lines:
        return dspy.Example(
            query=query, affected_stations=None, affected_lines=None
        ).with_inputs("query")  # pyright: ignore

    return dspy.Example(  # pyright: ignore
        query=query,
        affected_stations=AffectedStations(affected_stations=affected_stations),
        affected_lines=AffectedLines(affected_lines=affected_lines),
    ).with_inputs("query")


trainset = [
    create_dspy_example(
        "Trains from Newark and Harrison stations are once again departing from their normal tracks after an earlier service adjustment.",
        affected_stations=[PathStation.NWK, PathStation.HAR],
    ),
    create_dspy_example(
        "All service at Newport temporarily runs from JSQ-bound track. We will issue an update when trains resume service from their normal tracks.",
        affected_stations=[PathStation.NEW],
    ),
    create_dspy_example(
        "Weekdays through 11:59pm Thursday 8/28 due to ongoing switch repairs at Hoboken, PATH is offering customers the following additional options: NJT rail cross-honoring PATH customers at Hoboken, Secaucus, New York Penn Station; NJT Hudson-Bergen Light Rail cross-honoring PATH customers at Exchange Place, Newport and Hoboken; $3 discounted NY Waterway ferry tickets for PATH customers on select routes & times: Service between HOB/NJT Terminal-Brookfield Place: 6AM-10AM; 3PM-7PM and service between HOB/NJT Terminal-Midtown/W 39th St.: 6:55AM-9:35AM; 4:18PM-7:30PM. NOTES: Riders must show valid PATH fare payment card or virtual cross honoring pass on the RidePATH app to use $3 ferry ticket. NOTE: $3 ferry tickets available for purchase in the NYW app. Customers can also purchase the $3 ticket at NYW ferry ticket counters. PATH will also continue running 10-minute service on the HOB-WTC and HOB-33rd St. lines during rush hours through at least Labor Day, and normal scheduled service during all other times.",
        affected_lines=[PathLine.HOB_WTC, PathLine.HOB_33],
    ),
    create_dspy_example(
        "At JSQ, concourse elevator connecting platform with trks 3&4 back in service after earlier outage.",
        affected_stations=[PathStation.JSQ],
    ),
    create_dspy_example(
        "8/24 Sunday Daytime Service: NWK-WTC runs every 20 mins (9:08 AM-10:50 PM). JSQ-33 via HOB every 20 mins (9:20 AM-11:40 PM) plus extra HOB-33 every 10 mins. JSQ-bound 33-JSQ trains stop at Exch Pl through Jan 2026. Schedules: https://ow.ly/O8U450Uvg5f.",
        affected_lines=[PathLine.JSQ_33_HOB, PathLine.NWK_WTC, PathLine.HOB_33],
    ),
    create_dspy_example(
        "From 2:30-8:30am Sun 8/24 & 12:01-4:30am Mon 8/25, overnight svc btwn JSQ & 33 will not stop at Hoboken. Passengers traveling to/from HOB will transfer at Newport for a shuttle train traveling btwn NWPT & HOB. Change is needed to allow for track repairs.",
        affected_stations=[PathStation.HOB],
        affected_lines=[PathLine.JSQ_33_HOB],
    ),
    create_dspy_example(
        "Overnight trains may leave up to 10 minutes late for construction, maintenance, police inspections, or other operational issues. We’re sorry for the inconvenience.",
        affected_lines=[PathLine.JSQ_33_HOB, PathLine.NWK_WTC],
    ),
    create_dspy_example(
        "Service to/from Newark operates from H Platform and service to/from Harrison operates from NWK Bound Platform to accommodate construction at Harrison Station. We will issue an update when trains resume service from their normal platforms.",
        [PathStation.NWK, PathStation.HAR],
    ),
    create_dspy_example(
        "At Hoboken, elevator from street to fare zone is temporarily out of service",
        affected_stations=[PathStation.HOB],
    ),
    create_dspy_example(
        "Hoboken Station - Platform 1 Closure: From 11:59pm Friday 8/22/25 until 5:00am Mon 8/25/25: Platform 1 is closed for repairs. All service at Hoboken will operate from platforms 2 and 3. Please verify train destination before boarding.",
        affected_stations=[PathStation.HOB],
    ),
    create_dspy_example(
        "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure.",
        affected_stations=[PathStation.HOB],
        affected_lines=[PathLine.JSQ_33_HOB, PathLine.JSQ_33],
    ),
]


def metric_with_feedback(
    gold: dspy.Example,
    pred: dspy.Prediction,
    trace: DSPyTrace | None = None,
    pred_name: str | None = None,
    pred_trace: DSPyTrace | None = None,
):
    query: str = gold.query  # pyright: ignore
    gold_stations: AffectedStations | None = gold.affected_stations  # pyright: ignore
    gold_lines: AffectedLines | None = gold.affected_lines  # pyright: ignore
    pred_stations: AffectedStations | None = pred.affected_stations  # pyright: ignore
    pred_lines: AffectedLines | None = pred.affected_lines  # pyright: ignore

    # Handle None cases
    if (
        gold_stations is None
        and gold_lines is None
        and pred_stations is None
        and pred_lines is None
    ):
        return dspy.Prediction(score=1.0, feedback="Perfect!")

    if gold_stations is None and gold_lines is None:
        if pred_stations is not None or pred_lines is not None:
            return dspy.Prediction(score=0.0, feedback="Expected none.")

    gold_stations_set: Set[PathStation] = (
        set(gold_stations.affected_stations) if gold_stations else set()  # pyright: ignore
    )
    gold_lines_set: Set[PathLine] = (
        set(gold_lines.affected_lines) if gold_lines else set()  # pyright: ignore
    )
    pred_stations_set: Set[PathStation] = (
        set(pred_stations.affected_stations) if pred_stations else set()  # pyright: ignore
    )
    pred_lines_set: Set[PathLine] = (
        set(pred_lines.affected_lines) if pred_lines else set()  # pyright: ignore
    )

    gold_stations_and_lines = gold_stations_set.union(gold_lines_set)
    pred_stations_and_lines = pred_stations_set.union(pred_lines_set)

    missing_stations = gold_stations_set - pred_stations_set
    extra_stations = pred_stations_set - gold_stations_set
    missing_lines = gold_lines_set - pred_lines_set
    extra_lines = pred_lines_set - gold_lines_set

    intersection_stations_and_lines = len(
        gold_stations_and_lines.intersection(pred_stations_and_lines)
    )
    union_stations_and_lines = len(
        gold_stations_and_lines.union(pred_stations_and_lines)
    )
    jaccard_score = (
        intersection_stations_and_lines / union_stations_and_lines
        if union_stations_and_lines > 0
        else 1.0
    )

    if jaccard_score == 1.0:
        feedback = "Perfect match of affected stations and lines."
    else:
        feedback_parts: List[str] = []
        if missing_stations:
            feedback_parts.append(f"Missing stations: {', '.join(missing_stations)}")
        if extra_stations:
            feedback_parts.append(f"Extra stations: {', '.join(extra_stations)}")
            if (
                PathStation.EXP in extra_stations
                and query
                == "8/24 Sunday Daytime Service: NWK-WTC runs every 20 mins (9:08 AM-10:50 PM). JSQ-33 via HOB every 20 mins (9:20 AM-11:40 PM) plus extra HOB-33 every 10 mins. JSQ-bound 33-JSQ trains stop at Exch Pl through Jan 2026. Schedules: https://ow.ly/O8U450Uvg5f."
            ):
                feedback_parts.append(
                    "Exchange Place must not be included as riders at that station do not need to know that trains are going through there as they are already there to catch trains, so extra trains at that station are not important for them to know."
                )
        if missing_lines:
            feedback_parts.append(f"Missing lines: {', '.join(missing_lines)}")
            if (
                PathLine.JSQ_33_HOB in missing_lines
                and query
                == "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure."
            ):
                feedback_parts.append(
                    "JSQ_33_HOB must be included as riders must know that Hoboken is closed as they made be wanting to exit at that station."
                )
        if extra_lines:
            feedback_parts.append(f"Extra lines: {', '.join(extra_lines)}")
            if (
                PathLine.NWK_WTC in extra_lines
                and query
                == "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure."
            ):
                feedback_parts.append(
                    "NWK-WTC must not included because it is already running 24/7 on weekends, so nothing about the line is affected."
                )
        feedback = (
            "; ".join(feedback_parts)
            if feedback_parts
            else "Some stations and lines mismatch."
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
#     LLM.GEMINI_PRO,
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
        AffectedLines | None,
        AffectedStations | None,
        AffectedLines | None,
    ]
] = [
    (
        float(score),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        str(example.query),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        example.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        example.affected_lines,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        pred.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        pred.affected_lines,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    )
    for example, pred, score in unop_results.results  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType, reportUnknownVariableType]
]

print(unop_results_list)


teleprompter = dspy.GEPA(
    metric=metric_with_feedback,  # pyright: ignore[reportArgumentType]
    # auto="light",
    num_threads=32,
    reflection_lm=TEACHER_LLM,
    max_full_evals=TRAINING_N,
    use_wandb=True,
    wandb_api_key=os.environ["WANDB_API_KEY"],
)

print("\nStarting COPRO optimization...")
optimized_program: AffectedAreaPredictor = teleprompter.compile(  # pyright: ignore
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
        AffectedLines | None,
        AffectedStations | None,
        AffectedLines | None,
    ]
] = [
    (
        float(score),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        str(example.query),  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        example.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        example.affected_lines,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        pred.affected_stations,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
        pred.affected_lines,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    )
    for example, pred, score in op_results.results  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType, reportUnknownVariableType]
]

optimized_program(query="The trains are slow.")

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
prompt_hash = hashlib.sha1(optimized_prompt.encode()).hexdigest()[:8]

# Create filename with student-teacher-hash format
html_filename = f"dspy_affected_area_output/{student_model_name}-{teacher_model_name}-{prompt_hash}.html"


def format_result(result: AffectedStations | AffectedLines | None) -> str:
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
    else:
        sorted_lines = sorted(result.affected_lines, key=lambda r: r.value)
        lines_html = "".join(
            [
                f'<span class="pill line-pill" data-line="{r.name}">{html.escape(format_line_name(r.value))}</span>'
                for r in sorted_lines
            ]
        )
        return f'<div class="pill-container">{lines_html}</div>'


# Generate HTML file comparing results
def generate_results_html(
    unop_score: float,
    op_score: float,
    unop_results_list: list[
        tuple[
            float,
            str,
            AffectedStations | None,
            AffectedLines | None,
            AffectedStations | None,
            AffectedLines | None,
        ]
    ],
    op_results_list: list[
        tuple[
            float,
            str,
            AffectedStations | None,
            AffectedLines | None,
            AffectedStations | None,
            AffectedLines | None,
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
    <title>DSPy Affected Area Prediction Results</title>
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
    <h1>DSPy Affected Area Prediction Results Comparison</h1>

    <div class="model-info">
        <p><strong>Student Model:</strong> {student_model_name} {'<span class="reasoning-pill">Reasoning</span>' if student_uses_reasoning else ""}</p>
        <p><strong>Teacher Model:</strong> {teacher_model_name} {'<span class="reasoning-pill">Reasoning</span>' if teacher_uses_reasoning else ""}</p>
        <p><strong>Training Iterations:</strong> {TRAINING_N}</p>
    </div>


    <div class="model-info">
        <p>Unoptimized Score: <span class="score {get_score_class(unop_score / 100)}-text">{int(unop_score)}%</span></p>
        <p>Optimized Score: <span class="score {get_score_class(op_score / 100)}-text">{int(op_score)}%</span></p>
    </div>

    <h2>Detailed Results Comparison</h2>
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
        expected_lines,
        unop_pred_stations,
        unop_pred_lines,
    ) in enumerate(unop_results_list):
        if i < len(op_results_list):
            op_score, _, _, _, op_pred_stations, op_pred_lines = op_results_list[i]

            html_content += f"""
        <tr>
            <td>{html.escape(query)}</td>
            <td>
            {format_result(expected_stations)}
            {format_result(expected_lines)}
            </td>
            <td class="{get_score_class(op_score)}-cell">
            {format_result(op_pred_stations)}
            {format_result(op_pred_lines)}
                <div class="score-display">{op_score:.0%}</div>
            </td>
            <td class="{get_score_class(unop_score)}-cell">
                {format_result(unop_pred_stations)}
                {format_result(unop_pred_lines)}
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

    os.makedirs("dspy_affected_area_output", exist_ok=True)

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
    for file in glob.glob("dspy_affected_area_output/*.html"):
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
    <title>HTML Files Index</title>
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
    <h1>Generated HTML Files</h1>
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

        file_link = file.replace("dspy_affected_area_output/", "")
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
    with open("dspy_affected_area_output/index.html", "w") as f:
        f.write(html_content)

    print("Index file generated: index.html")


# Generate the HTML file
generate_results_html(
    unop_score=unop_results.score,
    op_score=op_results.score,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
    unop_results_list=unop_results_list,
    op_results_list=op_results_list,
    optimized_prompt=optimized_prompt,  # pyright: ignore[reportUnknownArgumentType, reportUnknownMemberType]
)
