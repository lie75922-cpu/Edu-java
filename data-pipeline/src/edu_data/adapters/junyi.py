from pathlib import Path
from typing import Iterable

from edu_data.canonical.schema import CanonicalExercise, CanonicalInteraction


class JunyiAdapter:
    """Boundary adapter for Junyi raw files.

    Parsing is intentionally not implemented in V0.1. DATA-0 must first inspect
    the actual downloaded files and freeze field/type assumptions.
    """

    def __init__(self, raw_dir: Path) -> None:
        self.raw_dir = raw_dir

    def validate_source(self) -> None:
        if not self.raw_dir.exists():
            raise FileNotFoundError(f"Junyi raw directory not found: {self.raw_dir}")

    def exercises(self) -> Iterable[CanonicalExercise]:
        raise NotImplementedError("Implement after DATA-0 raw schema audit")

    def interactions(self) -> Iterable[CanonicalInteraction]:
        raise NotImplementedError("Implement after DATA-0 raw schema audit")
