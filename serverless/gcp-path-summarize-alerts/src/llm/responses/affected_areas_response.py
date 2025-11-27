import json
from dataclasses import dataclass
from decimal import Decimal
from time import perf_counter
from typing import cast

from pydantic import ValidationError

from ..types import LlmType
from ...models import AffectedLines, AffectedStations
from ..clients.llm_client import GenerationResult
from ..clients.clients import gemini_flash_client, gpt_5_1_client
from .base import BaseResponse, LoggerLike

AREAS_TEMPLATE = BaseResponse.load_prompt("areas_prompt.txt")
ELEVATOR_AREAS_TEMPLATE = BaseResponse.load_prompt("elevator_areas_prompt.txt")


@dataclass(frozen=True)
class AffectedAreasResponse(BaseResponse):
    lines: AffectedLines | None
    stations: AffectedStations | None
    usage: GenerationResult
    llm_type: LlmType
    pricing: tuple[Decimal, Decimal]

    @classmethod
    def from_alert(  # type: ignore[override]  # pyright: ignore[reportIncompatibleMethodOverride,reportImplicitOverride]
        cls,
        alert: str,
        speed: LlmType,
        logger: LoggerLike | None = None,
        *args: object,
        **kwargs: object,
    ) -> "AffectedAreasResponse":
        _ = args
        _ = kwargs
        is_elevator = "elevator" in alert.lower()
        try:
            if is_elevator:
                client = gemini_flash_client()
                model_name = client.model_name
                prompt = ELEVATOR_AREAS_TEMPLATE.format(query=alert)
            else:
                client = (
                    gemini_flash_client() if speed is LlmType.FAST else gpt_5_1_client()
                )
                model_name = client.model_name
                prompt = AREAS_TEMPLATE.format(alert=alert)
            start = perf_counter()
            result = client.generate_json_with_usage(prompt)
            cls.log_llm_call(logger, "affected_areas", prompt, result, start, model_name)

            lines: AffectedLines | None = None
            stations: AffectedStations | None = None

            parsed_obj: dict[str, object] | None = None
            try:
                loaded = cast(object, json.loads(cls.strip_code_fence(result.text)))
                if isinstance(loaded, dict):
                    parsed_obj = cast(dict[str, object], loaded)
            except ValueError:
                parsed_obj = None

            if parsed_obj is not None:
                try:
                    lines = AffectedLines.model_validate(parsed_obj)
                except ValidationError:
                    lines = None
                try:
                    stations = AffectedStations.model_validate(parsed_obj)
                except ValidationError:
                    stations = None

            if lines is None:
                try:
                    lines = AffectedLines.model_validate_json(
                        cls.strip_code_fence(result.text)
                    )
                except (ValidationError, ValueError):
                    lines = None

            if stations is None:
                try:
                    stations = AffectedStations.model_validate_json(
                        cls.strip_code_fence(result.text)
                    )
                except (ValidationError, ValueError):
                    stations = None

            return cls(
                lines=lines,
                stations=stations,
                usage=result,
                llm_type=speed if not is_elevator else LlmType.FAST,
                pricing=client.pricing,
            )
        except KeyError:
            # If the model omits required keys (e.g., "affected_stations"), fail soft.
            return cls(
                lines=None,
                stations=None,
                usage=GenerationResult(text="", prompt_tokens=0, total_tokens=0),
                llm_type=speed if not is_elevator else LlmType.FAST,
                pricing=(Decimal("0"), Decimal("0")),  # pricing unused when result missing
            )
