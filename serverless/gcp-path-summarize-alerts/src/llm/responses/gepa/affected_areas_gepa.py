from __future__ import annotations

from collections.abc import Sequence
from typing import cast

import dspy
from dspy.teleprompt.gepa import GEPA
from typing_extensions import override

from src.models import AffectedLines, AffectedStations, PathLine, PathStation

from ..affected_areas_response import AffectedAreasSignature


class AffectedAreasProgram(dspy.Module):
    """Wraps the existing affected-areas signature for GEPA tuning."""

    def __init__(self, *, lm: dspy.LM | None = None) -> None:
        super().__init__()
        self._predict: dspy.Predict = dspy.Predict(AffectedAreasSignature)
        self._lm: dspy.LM | None = lm

    @override
    def forward(self, alert: str) -> dspy.Prediction:
        if self._lm is not None:
            with dspy.context(lm=self._lm):
                return self._predict(alert=alert)
        return self._predict(alert=alert)

    @property
    def predict(self) -> dspy.Predict:
        """Expose the underlying predictor (used by GEPA to access optimized prompt)."""
        return self._predict


def _example(
    alert: str,
    *,
    affected_stations: Sequence[PathStation] | None = None,
    affected_lines: Sequence[PathLine] | None = None,
) -> dspy.Example:
    stations = (
        AffectedStations(affected_stations=list(affected_stations))
        if affected_stations
        else None
    )
    lines = (
        AffectedLines(affected_lines=list(affected_lines)) if affected_lines else None
    )
    return dspy.Example(
        alert=alert,
        affected_stations=stations,
        affected_lines=lines,
    ).with_inputs("alert")


def build_default_affected_areas_trainset() -> list[dspy.Example]:
    """Lightweight default trainset to get started with GEPA."""
    return [
        _example(
            "Trains from Newark and Harrison stations are once again departing from their normal tracks after an earlier service adjustment.",
            affected_stations=[PathStation.NWK, PathStation.HAR],
        ),
        _example(
            "All service at Newport temporarily runs from JSQ-bound track. We will issue an update when trains resume service from their normal tracks.",
            affected_stations=[PathStation.NEW],
        ),
        _example(
            "Weekdays through 11:59pm Thursday 8/28 due to ongoing switch repairs at Hoboken, PATH is offering customers the following additional options: NJT rail cross-honoring PATH customers at Hoboken, Secaucus, New York Penn Station; NJT Hudson-Bergen Light Rail cross-honoring PATH customers at Exchange Place, Newport and Hoboken; $3 discounted NY Waterway ferry tickets for PATH customers on select routes & times: Service between HOB/NJT Terminal-Brookfield Place: 6AM-10AM; 3PM-7PM and service between HOB/NJT Terminal-Midtown/W 39th St.: 6:55AM-9:35AM; 4:18PM-7:30PM. NOTES: Riders must show valid PATH fare payment card or virtual cross honoring pass on the RidePATH app to use $3 ferry ticket. NOTE: $3 ferry tickets available for purchase in the NYW app. Customers can also purchase the $3 ticket at NYW ferry ticket counters. PATH will also continue running 10-minute service on the HOB-WTC and HOB-33rd St. lines during rush hours through at least Labor Day, and normal scheduled service during all other times.",
            affected_lines=[PathLine.HOB_WTC, PathLine.HOB_33],
        ),
        _example(
            "8/24 Sunday Daytime Service: NWK-WTC runs every 20 mins (9:08 AM-10:50 PM). JSQ-33 via HOB every 20 mins (9:20 AM-11:40 PM) plus extra HOB-33 every 10 mins. JSQ-bound 33-JSQ trains stop at Exch Pl through Jan 2026. Schedules: https://ow.ly/O8U450Uvg5f.",
            affected_lines=[PathLine.JSQ_33_HOB, PathLine.NWK_WTC, PathLine.HOB_33],
        ),
        _example(
            "From 2:30-8:30am Sun 8/24 & 12:01-4:30am Mon 8/25, overnight svc btwn JSQ & 33 will not stop at Hoboken. Passengers traveling to/from HOB will transfer at Newport for a shuttle train traveling btwn NWPT & HOB. Change is needed to allow for track repairs.",
            affected_stations=[PathStation.HOB],
            affected_lines=[PathLine.JSQ_33_HOB],
        ),
        _example(
            "Overnight trains may leave up to 10 minutes late for construction, maintenance, police inspections, or other operational issues. We’re sorry for the inconvenience.",
            affected_lines=[PathLine.JSQ_33_HOB, PathLine.NWK_WTC],
        ),
        _example(
            "Service to/from Newark operates from H Platform and service to/from Harrison operates from NWK Bound Platform to accommodate construction at Harrison Station. We will issue an update when trains resume service from their normal platforms.",
            affected_stations=[PathStation.NWK, PathStation.HAR],
        ),
        _example(
            "Hoboken Station - Platform 1 Closure: From 11:59pm Friday 8/22/25 until 5:00am Mon 8/25/25: Platform 1 is closed for repairs. All service at Hoboken will operate from platforms 2 and 3. Please verify train destination before boarding.",
            affected_stations=[PathStation.HOB],
        ),
        _example(
            "Hoboken Station Closed until 5am 9/2/25. JSQ-33 and NWK-WTC running 24/7 during this time. (Fri 8/29 7am-9pm Special WTC-33 service running.) http://www.panynj.gov/HobokenClosure.",
            affected_stations=[PathStation.HOB],
            affected_lines=[PathLine.JSQ_33_HOB, PathLine.JSQ_33],
        ),
        _example(
            "All service at 14 St operates from NJ-bound track until 5am. The entrances to the NJ-bound track are on the west side of 6 Av.",
            affected_stations=[PathStation.S14],
        ),
        _example(
            "9th St and 23rd St which usually closed nightly will remain open from Wednesday 11:59pm 11-26-25 and closures will resume 11:59pm Monday 12-01-25.",
            affected_stations=[PathStation.S09, PathStation.S23],
        ),
    ]


def build_default_affected_areas_valset() -> list[dspy.Example]:
    """Validation set distinct from the trainset to check generalization."""

    return [
        _example(
            "Trains at Newport are once again departing from their normal tracks after an earlier service adjustment.",
            affected_stations=[PathStation.NWK],
        ),
        _example(
            "11/29-11/30 Weekend Service: JSQ-33 via HOB and NWK-WTC running both ways. NWK-WTC every 20. JSQ-33 via HOB every 20, stops at Ex Pl toward JSQ thru January 2026. Additional HOB-33 every 10 min. Schedules: https://ow.ly/9ffE50Xy5r8.",
            affected_stations=[PathStation.NWK],
        ),
    ]


def metric_affected_areas(
    gold: dspy.Example,
    pred: dspy.Prediction,
    _trace: object | None = None,
    _pred_name: str | None = None,
    _pred_trace: object | None = None,
) -> dspy.Prediction:
    """Simple Jaccard-based metric with feedback for GEPA."""

    def _to_sets(
        stations: AffectedStations | None, lines: AffectedLines | None
    ) -> tuple[set[PathStation], set[PathLine]]:
        station_set: set[PathStation] = (
            set(stations.affected_stations) if stations else set()
        )
        line_set: set[PathLine] = set(lines.affected_lines) if lines else set()
        return station_set, line_set

    gold_stations, gold_lines = _to_sets(
        cast(AffectedStations | None, getattr(gold, "affected_stations", None)),
        cast(AffectedLines | None, getattr(gold, "affected_lines", None)),
    )
    pred_stations, pred_lines = _to_sets(
        cast(AffectedStations | None, getattr(pred, "affected_stations", None)),
        cast(AffectedLines | None, getattr(pred, "affected_lines", None)),
    )

    missing = (gold_stations | gold_lines) - (pred_stations | pred_lines)
    extra = (pred_stations | pred_lines) - (gold_stations | gold_lines)
    inter = (gold_stations | gold_lines) & (pred_stations | pred_lines)
    union = (gold_stations | gold_lines) | (pred_stations | pred_lines)
    jaccard = len(inter) / len(union) if union else 1.0

    parts: list[str] = []
    if missing:
        parts.append(f"Missing: {', '.join(sorted(x.name for x in missing))}")
    if extra:
        parts.append(f"Extra: {', '.join(sorted(x.name for x in extra))}")
    feedback = "; ".join(parts) if parts else "Perfect!"
    return dspy.Prediction(score=jaccard, feedback=feedback)


def optimize_affected_areas(
    *,
    student_lm: dspy.LM | None = None,
    teacher_lm: dspy.LM | None = None,
    max_full_evals: int = 40,
    num_threads: int = 8,
) -> AffectedAreasProgram:
    """Run GEPA to optimize the affected-areas prompt/program.

    Uses the default trainset for GEPA's reflection/optimization loop and
    evaluates compilation against the default valset plus the trainset. Caller
    is responsible for any evaluations on the combined dataset.
    """
    trainset_list = build_default_affected_areas_trainset()
    valset_list = build_default_affected_areas_valset()

    if student_lm:
        dspy.settings.configure(lm=student_lm)

    program = AffectedAreasProgram(lm=student_lm)

    teleprompter = GEPA(
        metric=metric_affected_areas,
        reflection_lm=teacher_lm,
        max_full_evals=max_full_evals,
        num_threads=num_threads,
    )
    evalset = valset_list + trainset_list

    optimized_program = cast(
        AffectedAreasProgram,
        teleprompter.compile(
            program,
            trainset=trainset_list,
            valset=evalset,
        ),
    )
    return optimized_program
