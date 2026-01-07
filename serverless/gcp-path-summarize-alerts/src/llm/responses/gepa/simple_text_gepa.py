from __future__ import annotations

from typing import cast

import dspy
from dspy.teleprompt.gepa import GEPA
from typing_extensions import override

from src.llm.responses.preprocess_response import PreprocessSignature
from src.llm.responses.remove_single_area_response import RemoveSingleAreaSignature


class PreprocessProgram(dspy.Module):
    """Wrap PreprocessSignature for GEPA tuning."""

    def __init__(self, *, lm: dspy.LM | None = None) -> None:
        super().__init__()
        self._predict: dspy.Predict = dspy.Predict(PreprocessSignature)
        self._lm: dspy.LM | None = lm

    @override
    def forward(self, alert: str) -> dspy.Prediction:
        if self._lm is not None:
            with dspy.context(lm=self._lm):
                return self._predict(alert=alert)
        return self._predict(alert=alert)

    @property
    def predict(self) -> dspy.Predict:
        return self._predict


class RemoveSingleAreaProgram(dspy.Module):
    """Wrap RemoveSingleAreaSignature for GEPA tuning."""

    def __init__(self, *, lm: dspy.LM | None = None) -> None:
        super().__init__()
        self._predict: dspy.Predict = dspy.Predict(RemoveSingleAreaSignature)
        self._lm: dspy.LM | None = lm

    @override
    def forward(self, text: str, single_area: str) -> dspy.Prediction:
        if self._lm is not None:
            with dspy.context(lm=self._lm):
                return self._predict(text=text, single_area=single_area)
        return self._predict(text=text, single_area=single_area)

    @property
    def predict(self) -> dspy.Predict:
        return self._predict


def _preprocess_example(alert: str, cleaned_text: str) -> dspy.Example:
    return dspy.Example(alert=alert, cleaned_text=cleaned_text).with_inputs("alert")


def _remove_single_area_example(
    text: str, single_area: str, updated_text: str
) -> dspy.Example:
    return dspy.Example(
        text=text,
        single_area=single_area,
        updated_text=updated_text,
    ).with_inputs("text", "single_area")


def build_default_preprocess_trainset() -> list[dspy.Example]:
    """Trainset for preprocess prompt optimization."""
    return [
        _preprocess_example(
            "Tips: 15 min delay JSQ-33. Riders should allow extra travel time. Update will follow.",
            "15 min delay JSQ-33.",
        ),
        _preprocess_example(
            "Service alert: 10 min delay due to track inspection. Tips: use alternate routes. We'll provide an update later.",
            "Service alert: 10 min delay due to track inspection. Use alternate routes.",
        ),
        _preprocess_example(
            "PATH alert: residual delays up to 20 minutes. Tips: add extra travel time. Update to follow.",
            "PATH alert: residual delays up to 20 minutes.",
        ),
        _preprocess_example(
            "Weather-related delays across the system. Tips: consider earlier trains and expect extra time. We'll issue an update.",
            "Weather-related delays across the system. Consider earlier trains.",
        ),
        _preprocess_example(
            "Minor delays while we single-track near Newark. Tips for riders: leave earlier than usual. Further updates later today.",
            "Minor delays while we single-track near Newark.",
        ),
        _preprocess_example(
            "Track maintenance causing 5-10 minute delays. Tips: check next-arrival boards. Update will be issued later.",
            "Track maintenance causing 5-10 minute delays. Check next-arrival boards.",
        ),
    ]


def build_default_preprocess_valset() -> list[dspy.Example]:
    return [
        _preprocess_example(
            "Tips: trains may be 10 minutes late after police activity. Update later this afternoon.",
            "Trains may be 10 minutes late after police activity.",
        ),
        _preprocess_example(
            "Signal issue causing 8 minute delay. Tips: use alternate elevators. We'll share an update soon.",
            "Signal issue causing 8 minute delay. Use alternate elevators.",
        ),
        _preprocess_example(
            "Tips: minor hold at Hoboken. Expect brief delays. Update will follow.",
            "Minor hold at Hoboken. Brief delays.",
        ),
    ]


def build_default_remove_single_area_trainset() -> list[dspy.Example]:
    """Trainset for remove-single-area prompt optimization."""
    return [
        _remove_single_area_example(
            "Service change: WTC trains delayed; JSQ running on time. Tips: allow extra travel time.",
            "WTC",
            "JSQ running on time.",
        ),
        _remove_single_area_example(
            "Hoboken outage resolved. Update later today.",
            "Hoboken",
            "Outage resolved.",
        ),
        _remove_single_area_example(
            "WTC elevator out; 33 St elevator fine.",
            "WTC",
            "33 St elevator fine.",
        ),
        _remove_single_area_example(
            "No mention of target here.",
            "Newark",
            "No mention of target here.",
        ),
        _remove_single_area_example(
            "Service at Newport and Hoboken suspended; Newport expected back soon.",
            "Newport",
            "Service at Hoboken suspended; expected back soon.",
        ),
        _remove_single_area_example(
            "Exchange Place service is normal. PATH alerts remain at WTC.",
            "WTC",
            "Exchange Place service is normal.",
        ),
    ]


def build_default_remove_single_area_valset() -> list[dspy.Example]:
    return []


def _text_metric_factory(field_name: str):
    def _metric(
        gold: dspy.Example,
        pred: dspy.Prediction,
        _trace: object | None = None,
        _pred_name: str | None = None,
        _pred_trace: object | None = None,
    ) -> dspy.Prediction:
        gold_text = cast(str, getattr(gold, field_name, "") or "")
        pred_text = cast(str, getattr(pred, field_name, "") or "")

        def _norm(s: str) -> set[str]:
            return set(s.lower().split())

        gold_tokens = _norm(gold_text)
        pred_tokens = _norm(pred_text)

        missing = gold_tokens - pred_tokens
        extra = pred_tokens - gold_tokens
        inter = gold_tokens & pred_tokens
        union = gold_tokens | pred_tokens
        jaccard = len(inter) / len(union) if union else 1.0

        parts: list[str] = []
        if missing:
            parts.append(f"Missing: {', '.join(sorted(missing))}")
        if extra:
            parts.append(f"Extra: {', '.join(sorted(extra))}")
        feedback = "; ".join(parts) if parts else "Perfect!"
        return dspy.Prediction(score=jaccard, feedback=feedback)

    return _metric


metric_preprocess = _text_metric_factory("cleaned_text")
metric_remove_single_area = _text_metric_factory("updated_text")


def optimize_preprocess(
    *,
    student_lm: dspy.LM | None = None,
    teacher_lm: dspy.LM | None = None,
    max_full_evals: int = 40,
    num_threads: int = 8,
) -> PreprocessProgram:
    """Run GEPA for the preprocess prompt/program."""
    trainset_list = build_default_preprocess_trainset()
    valset_list = build_default_preprocess_valset()

    if student_lm:
        dspy.settings.configure(lm=student_lm)

    program = PreprocessProgram(lm=student_lm)
    teleprompter = GEPA(
        metric=metric_preprocess,
        reflection_lm=teacher_lm,
        max_full_evals=max_full_evals,
        num_threads=num_threads,
    )
    evalset = valset_list + trainset_list

    optimized_program = cast(
        PreprocessProgram,
        teleprompter.compile(
            program,
            trainset=trainset_list,
            valset=evalset,
        ),
    )
    return optimized_program


def optimize_remove_single_area(
    *,
    student_lm: dspy.LM | None = None,
    teacher_lm: dspy.LM | None = None,
    max_full_evals: int = 40,
    num_threads: int = 8,
) -> RemoveSingleAreaProgram:
    """Run GEPA for the remove-single-area prompt/program."""
    trainset_list = build_default_remove_single_area_trainset()
    valset_list = build_default_remove_single_area_valset()

    if student_lm:
        dspy.settings.configure(lm=student_lm)

    program = RemoveSingleAreaProgram(lm=student_lm)
    teleprompter = GEPA(
        metric=metric_remove_single_area,
        reflection_lm=teacher_lm,
        max_full_evals=max_full_evals,
        num_threads=num_threads,
    )
    evalset = valset_list + trainset_list

    optimized_program = cast(
        RemoveSingleAreaProgram,
        teleprompter.compile(
            program,
            trainset=trainset_list,
            valset=evalset,
        ),
    )
    return optimized_program
