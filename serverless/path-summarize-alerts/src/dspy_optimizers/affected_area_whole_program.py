# from enum import Enum
# from typing import Any, List, Set
# import dspy  # pyright: ignore[reportMissingTypeStubs]
# import os
# import html
# import hashlib
# import glob
# from datetime import datetime

# from dspy.teleprompt.gepa.gepa_utils import DSPyTrace  # pyright: ignore[reportMissingTypeStubs]
# from pydantic import BaseModel

# from gepa import optimize # pyright: ignore[reportMissingTypeStubs]
# from gepa.adapters.dspy_full_program_adapter.full_program_adapter import DspyAdapter # pyright: ignore[reportMissingTypeStubs]


# class PathLine(str, Enum):
#     """
#     Enum representing PATH lines.
#     """

#     NWK_WTC = "NWK_WTC"
#     JSQ_WTC = "JSQ_WTC"
#     HOB_WTC = "HOB_WTC"
#     JSQ_33 = "JSQ_33"
#     HOB_33 = "HOB_33"
#     JSQ_33_HOB = "JSQ_33_HOB"


# class PathStation(str, Enum):
#     """Enum representing PATH stations."""

#     NWK = "Newark Penn Station"
#     HAR = "Harrison"
#     JSQ = "Journal Square"
#     GRV = "Grove Street"
#     EXP = "Exchange Place"
#     WTC = "World Trade Center"
#     HOB = "Hoboken"
#     NEW = "Newport"
#     CHR = "Christopher Street"
#     S09 = "9th Street"
#     S14 = "14th Street"
#     S23 = "23rd Street"
#     S33 = "33rd Street"


# class AffectedLines(BaseModel):
#     affected_lines: List[PathLine]


# class AffectedStations(BaseModel):
#     affected_stations: List[PathStation]

# def load_dspy_affected_area_file() -> str:
#     """Load the first 120 lines of dspy_affected_area.py into a string."""
#     try:
#         with open("src/dspy_affected_area.py", "r") as file:
#             lines = [next(file) for _ in range(120)]
#         return "".join(lines)
#     except FileNotFoundError:
#         raise FileNotFoundError("src/dspy_affected_area.py not found in the current directory.")

# seed_program = load_dspy_affected_area_file()


# # 1. Set up OpenAI Model
# def create_dspy_lm(
#     model: str, temperature: float = 1.0, use_reasoning: bool = True
# ) -> dspy.LM:
#     """Helper function to create dspy.LM instances with common parameters."""
#     params: dict[str, Any] = {
#         "model": model,
#         "api_base": "https://openrouter.ai/api/v1",
#         "api_key": openrouter_key,
#         "temperature": temperature,
#     }

#     if use_reasoning:
#         params["reasoning"] = {"max_tokens": 20000}
#         params["max_tokens"] = 20000

#     return dspy.LM(**params)


# openrouter_key = os.environ.get("OPENROUTER_API_KEY")
# if not os.environ.get("OPENROUTER_API_KEY"):
#     raise ValueError("OPENROUTER_API_KEY environment variable not set.")

# gpt_oss_20b = create_dspy_lm("openrouter/openai/gpt-oss-20b:price", use_reasoning=False)
# gpt_oss_120b = create_dspy_lm(
#     "openrouter/openai/gpt-oss-120b:price", use_reasoning=False
# )
# gemini_flash = create_dspy_lm("openrouter/google/gemini-2.5-flash")
# gemini_flash_no_reasoning = create_dspy_lm(
#     "openrouter/google/gemini-2.5-flash", use_reasoning=False
# )
# gemini_flash_lite = create_dspy_lm(
#     "openrouter/google/gemini-2.5-flash-lite", use_reasoning=False
# )
# gemini_pro = create_dspy_lm("openrouter/google/gemini-2.5-pro")
# gpt5 = create_dspy_lm("openrouter/openai/gpt-5", temperature=1.0)
# gpt5_nano = create_dspy_lm("openrouter/openai/gpt-5-nano", temperature=1.0)


# def create_dspy_example(
#     query: str,
#     affected_stations: List[PathStation] = [],
#     affected_lines: List[PathLine] = [],
# ) -> dspy.Example:
#     if not affected_stations and not affected_lines:
#         return dspy.Example(
#             query=query, affected_stations=None, affected_lines=None
#         ).with_inputs("query")  # pyright: ignore

#     return dspy.Example(  # pyright: ignore
#         query=query,
#         affected_stations=AffectedStations(affected_stations=affected_stations),
#         affected_lines=AffectedLines(affected_lines=affected_lines),
#     ).with_inputs("query")


# trainset = [
#     create_dspy_example(
#         "Trains from Newark and Harrison stations are once again departing from their normal tracks after an earlier service adjustment.",
#         affected_stations=[PathStation.NWK, PathStation.HAR],
#     ),
#     create_dspy_example(
#         "All service at Newport temporarily runs from JSQ-bound track. We will issue an update when trains resume service from their normal tracks.",
#         affected_stations=[PathStation.NEW],
#     ),
#     create_dspy_example(
#         "Weekdays through 11:59pm Thursday 8/28 due to ongoing switch repairs at Hoboken, PATH is offering customers the following additional options: NJT rail cross-honoring PATH customers at Hoboken, Secaucus, New York Penn Station; NJT Hudson-Bergen Light Rail cross-honoring PATH customers at Exchange Place, Newport and Hoboken; $3 discounted NY Waterway ferry tickets for PATH customers on select routes & times: Service between HOB/NJT Terminal-Brookfield Place: 6AM-10AM; 3PM-7PM and service between HOB/NJT Terminal-Midtown/W 39th St.: 6:55AM-9:35AM; 4:18PM-7:30PM. NOTES: Riders must show valid PATH fare payment card or virtual cross honoring pass on the RidePATH app to use $3 ferry ticket. NOTE: $3 ferry tickets available for purchase in the NYW app. Customers can also purchase the $3 ticket at NYW ferry ticket counters. PATH will also continue running 10-minute service on the HOB-WTC and HOB-33rd St. lines during rush hours through at least Labor Day, and normal scheduled service during all other times.",
#         affected_lines=[PathLine.HOB_WTC, PathLine.HOB_33],
#     ),
#     create_dspy_example(
#         "At JSQ, concourse elevator connecting platform with trks 3&4 back in service after earlier outage.",
#         affected_stations=[PathStation.JSQ],
#     ),
#     create_dspy_example(
#         "8/24 Sunday Daytime Service: NWK-WTC runs every 20 mins (9:08 AM-10:50 PM). JSQ-33 via HOB every 20 mins (9:20 AM-11:40 PM) plus extra HOB-33 every 10 mins. JSQ-bound 33-JSQ trains stop at Exch Pl through Jan 2026. Schedules: https://ow.ly/O8U450Uvg5f.",
#         affected_lines=[PathLine.JSQ_33_HOB, PathLine.NWK_WTC, PathLine.HOB_33],
#     ),
#     create_dspy_example(
#         "From 2:30-8:30am Sun 8/24 & 12:01-4:30am Mon 8/25, overnight svc btwn JSQ & 33 will not stop at Hoboken. Passengers traveling to/from HOB will transfer at Newport for a shuttle train traveling btwn NWPT & HOB. Change is needed to allow for track repairs.",
#         affected_stations=[PathStation.HOB],
#         affected_lines=[PathLine.JSQ_33_HOB],
#     ),
#     create_dspy_example(
#         "Overnight trains may leave up to 10 minutes late for construction, maintenance, police inspections, or other operational issues. We’re sorry for the inconvenience.",
#         affected_lines=[PathLine.JSQ_33_HOB, PathLine.NWK_WTC],
#     ),
#     create_dspy_example(
#         "Service to/from Newark operates from H Platform and service to/from Harrison operates from NWK Bound Platform to accommodate construction at Harrison Station. We will issue an update when trains resume service from their normal platforms.",
#         [PathStation.NWK, PathStation.HAR],
#     ),
#     create_dspy_example(
#         "At Hoboken, elevator from street to fare zone is temporarily out of service",
#         affected_stations=[PathStation.HOB],
#     ),
#     create_dspy_example(
#         "Hoboken Station - Platform 1 Closure: From 11:59pm Friday 8/22/25 until 5:00am Mon 8/25/25: Platform 1 is closed for repairs. All service at Hoboken will operate from platforms 2 and 3. Please verify train destination before boarding.",
#         affected_stations=[PathStation.HOB],
#     ),
#     create_dspy_example(
#         "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure.",
#         affected_stations=[PathStation.HOB],
#         affected_lines=[PathLine.JSQ_33_HOB, PathLine.JSQ_33],
#     ),
# ]

# def metric_with_feedback(
#     gold: dspy.Example,
#     pred: dspy.Prediction,
#     trace: DSPyTrace | None = None,
#     pred_name: str | None = None,
#     pred_trace: DSPyTrace | None = None,
# ):
#     query: str = gold.query  # pyright: ignore
#     gold_stations: AffectedStations | None = gold.affected_stations  # pyright: ignore
#     gold_lines: AffectedLines | None = gold.affected_lines  # pyright: ignore
#     pred_stations: AffectedStations | None = pred.affected_stations  # pyright: ignore
#     pred_lines: AffectedLines | None = pred.affected_lines  # pyright: ignore

#     # Handle None cases
#     if (
#         gold_stations is None
#         and gold_lines is None
#         and pred_stations is None
#         and pred_lines is None
#     ):
#         return dspy.Prediction(score=1.0, feedback="Perfect!")

#     if gold_stations is None and gold_lines is None:
#         if pred_stations is not None or pred_lines is not None:
#             return dspy.Prediction(score=0.0, feedback="Expected none.")

#     gold_stations_set: Set[PathStation] = (
#         set(gold_stations.affected_stations) if gold_stations else set()  # pyright: ignore
#     )
#     gold_lines_set: Set[PathLine] = (
#         set(gold_lines.affected_lines) if gold_lines else set()  # pyright: ignore
#     )
#     pred_stations_set: Set[PathStation] = (
#         set(pred_stations.affected_stations) if pred_stations else set()  # pyright: ignore
#     )
#     pred_lines_set: Set[PathLine] = (
#         set(pred_lines.affected_lines) if pred_lines else set()  # pyright: ignore
#     )

#     gold_stations_and_lines = gold_stations_set.union(gold_lines_set)
#     pred_stations_and_lines = pred_stations_set.union(pred_lines_set)

#     missing_stations = gold_stations_set - pred_stations_set
#     extra_stations = pred_stations_set - gold_stations_set
#     missing_lines = gold_lines_set - pred_lines_set
#     extra_lines = pred_lines_set - gold_lines_set

#     intersection_stations_and_lines = len(
#         gold_stations_and_lines.intersection(pred_stations_and_lines)
#     )
#     union_stations_and_lines = len(
#         gold_stations_and_lines.union(pred_stations_and_lines)
#     )
#     jaccard_score = (
#         intersection_stations_and_lines / union_stations_and_lines
#         if union_stations_and_lines > 0
#         else 1.0
#     )

#     if jaccard_score == 1.0:
#         feedback = "Perfect match of affected stations and lines."
#     else:
#         feedback_parts: List[str] = []
#         if missing_stations:
#             feedback_parts.append(f"Missing stations: {', '.join(missing_stations)}")
#         if extra_stations:
#             feedback_parts.append(f"Extra stations: {', '.join(extra_stations)}")
#             if (
#                 PathStation.EXP in extra_stations
#                 and query
#                 == "8/24 Sunday Daytime Service: NWK-WTC runs every 20 mins (9:08 AM-10:50 PM). JSQ-33 via HOB every 20 mins (9:20 AM-11:40 PM) plus extra HOB-33 every 10 mins. JSQ-bound 33-JSQ trains stop at Exch Pl through Jan 2026. Schedules: https://ow.ly/O8U450Uvg5f."
#             ):
#                 feedback_parts.append(
#                     "Exchange Place must not be included as riders at that station do not need to know that trains are going through there as they are already there to catch trains, so extra trains at that station are not important for them to know."
#                 )
#         if missing_lines:
#             feedback_parts.append(f"Missing lines: {', '.join(missing_lines)}")
#             if (
#                 PathLine.JSQ_33_HOB in missing_lines
#                 and query
#                 == "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure."
#             ):
#                 feedback_parts.append(
#                     "JSQ_33_HOB must be included as riders must know that Hoboken is closed as they made be wanting to exit at that station."
#                 )
#         if extra_lines:
#             feedback_parts.append(f"Extra lines: {', '.join(extra_lines)}")
#             if (
#                 PathLine.NWK_WTC in extra_lines
#                 and query
#                 == "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure."
#             ):
#                 feedback_parts.append(
#                     "NWK-WTC must not included because it is already running 24/7 on weekends, so nothing about the line is affected."
#                 )
#         feedback = (
#             "; ".join(feedback_parts)
#             if feedback_parts
#             else "Some stations and lines mismatch."
#         )

#     return dspy.Prediction(score=jaccard_score, feedback=feedback)


# reflection_lm = gemini_flash
# adapter = DspyAdapter(
#     task_lm=gemini_flash_lite, # <-- This LM will be used for the downstream task
#     metric_fn=metric_with_feedback,
#     reflection_lm=lambda x: reflection_lm(x)[0],
#     # failure_score=0.999,
#     num_threads=16,
# )
# print(adapter.evaluate(trainset, {"program": seed_program}))
# result = optimize(
#     seed_candidate={"program": seed_program},
#     trainset=trainset,
#     valset=trainset,
#     adapter=adapter,
#     max_metric_calls=500,
#     display_progress_bar=True,
#     use_wandb=True,
#     wandb_api_key=os.environ["WANDB_API_KEY"],
# )
# # Get the evolved program
# optimized_program_code = result.best_candidate["program"]
# if optimized_program_code.startswith("python"):
#     optimized_program_code = optimized_program_code[len("python") :].lstrip()
# print(optimized_program_code)

# print(result.best_outputs_valset)
# print(result.val_aggregate_scores)
# print(result.best_idx)

# print("unop prog")
# print(adapter.evaluate(trainset, {"program": seed_program}))

# print("op prog")
# print(adapter.evaluate(trainset, {"program": optimized_program_code}))
