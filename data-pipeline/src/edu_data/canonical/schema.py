from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(frozen=True)
class CanonicalExercise:
    exercise_external_id: str
    name: str
    topic: Optional[str] = None
    area: Optional[str] = None
    prerequisite_external_id: Optional[str] = None

    def __post_init__(self) -> None:
        if not self.exercise_external_id.strip():
            raise ValueError("exercise_external_id must not be blank")


@dataclass(frozen=True)
class CanonicalInteraction:
    student_external_id: str
    exercise_external_id: str
    correct: bool
    occurred_at: datetime
    attempts: Optional[int] = None
    duration_seconds: Optional[float] = None
    hint_used: Optional[bool] = None
    count_hints: Optional[int] = None

    def __post_init__(self) -> None:
        if not self.student_external_id.strip():
            raise ValueError("student_external_id must not be blank")
        if not self.exercise_external_id.strip():
            raise ValueError("exercise_external_id must not be blank")
        if self.attempts is not None and self.attempts < 0:
            raise ValueError("attempts must be non-negative")
