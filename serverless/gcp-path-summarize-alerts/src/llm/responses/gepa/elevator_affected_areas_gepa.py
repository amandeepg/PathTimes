from __future__ import annotations

from collections.abc import Sequence
from typing import cast

import dspy
from dspy.teleprompt.gepa import GEPA
from typing_extensions import override

from src.models import AffectedStations, PathStation

from ..elevator_affected_areas_response import ElevatorAffectedAreasSignature


class ElevatorAffectedAreasProgram(dspy.Module):
    """Wrap the elevator affected-areas signature for GEPA tuning."""

    def __init__(self, *, lm: dspy.LM | None = None) -> None:
        super().__init__()
        self._predict: dspy.Predict = dspy.Predict(ElevatorAffectedAreasSignature)
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
    alert: str, *, affected_stations: Sequence[PathStation] | None = None
) -> dspy.Example:
    stations = (
        AffectedStations(affected_stations=list(affected_stations))
        if affected_stations
        else None
    )
    return dspy.Example(alert=alert, affected_stations=stations).with_inputs("alert")


def build_default_elevator_affected_areas_trainset() -> list[dspy.Example]:
    """Default trainset for elevator-specific GEPA runs."""

    def create_dspy_example(
        alert: str, *, affected_stations: Sequence[PathStation]
    ) -> dspy.Example:
        return _example(alert, affected_stations=affected_stations)

    return [
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


def build_default_elevator_affected_areas_valset() -> list[dspy.Example]:
    """Small validation set to sanity check elevator GEPA runs."""
    return [
        _example(
            "Street-to-mezz elevator at 14th Street is out of service until further notice.",
            affected_stations=[PathStation.S14],
        ),
        _example(
            "Exchange Place elevators are back in service after this morning's cleaning.",
            affected_stations=[PathStation.EXP],
        ),
        _example(
            "Christopher St elevator restored; 9th St elevator remains unavailable.",
            affected_stations=[PathStation.CHR, PathStation.S09],
        ),
    ]


def metric_elevator_affected_areas(
    gold: dspy.Example,
    pred: dspy.Prediction,
    _trace: object | None = None,
    _pred_name: str | None = None,
    _pred_trace: object | None = None,
) -> dspy.Prediction:
    """Jaccard-based metric with feedback for elevator-only datasets."""

    def _to_set(stations: AffectedStations | None) -> set[PathStation]:
        return set(stations.affected_stations) if stations else set()

    gold_stations = _to_set(
        cast(AffectedStations | None, getattr(gold, "affected_stations", None))
    )
    pred_stations = _to_set(
        cast(AffectedStations | None, getattr(pred, "affected_stations", None))
    )

    missing = gold_stations - pred_stations
    extra = pred_stations - gold_stations
    inter = gold_stations & pred_stations
    union = gold_stations | pred_stations
    jaccard = len(inter) / len(union) if union else 1.0

    parts: list[str] = []
    if missing:
        parts.append(f"Missing: {', '.join(sorted(x.name for x in missing))}")
    if extra:
        parts.append(f"Extra: {', '.join(sorted(x.name for x in extra))}")
    feedback = "; ".join(parts) if parts else "Perfect!"
    return dspy.Prediction(score=jaccard, feedback=feedback)


def optimize_elevator_affected_areas(
    *,
    student_lm: dspy.LM | None = None,
    teacher_lm: dspy.LM | None = None,
    max_full_evals: int = 40,
    num_threads: int = 8,
) -> ElevatorAffectedAreasProgram:
    """Run GEPA to optimize the elevator-affected-areas prompt/program."""

    trainset_list = build_default_elevator_affected_areas_trainset()
    valset_list = build_default_elevator_affected_areas_valset()

    if student_lm:
        dspy.settings.configure(lm=student_lm)

    program = ElevatorAffectedAreasProgram(lm=student_lm)

    teleprompter = GEPA(
        metric=metric_elevator_affected_areas,
        reflection_lm=teacher_lm,
        max_full_evals=max_full_evals,
        num_threads=num_threads,
    )
    evalset = valset_list + trainset_list

    optimized_program = cast(
        ElevatorAffectedAreasProgram,
        teleprompter.compile(
            program,
            trainset=trainset_list,
            valset=evalset,
        ),
    )
    return optimized_program
