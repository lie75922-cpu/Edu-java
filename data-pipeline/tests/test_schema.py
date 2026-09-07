from datetime import datetime, timezone

import pytest

from edu_data.canonical.schema import CanonicalExercise, CanonicalInteraction


def test_exercise_requires_external_id() -> None:
    with pytest.raises(ValueError):
        CanonicalExercise(exercise_external_id=" ", name="demo")


def test_interaction_accepts_minimum_realistic_fields() -> None:
    item = CanonicalInteraction(
        student_external_id="student-1",
        exercise_external_id="exercise-1",
        correct=True,
        occurred_at=datetime.now(timezone.utc),
    )
    assert item.correct is True
