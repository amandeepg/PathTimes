from typing import Any

from ... import LM, Module

class GEPA:
    def __init__(
        self,
        metric: Any,
        reflection_lm: LM | None = ...,
        max_full_evals: int | None = ...,
        num_threads: int | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def compile(
        self, student: Module, trainset: Any, *, valset: Any | None = ...
    ) -> Module: ...
