package com.smartlearning.research;

import java.util.List;

public record ResearchExerciseCatalog(
        int schemaVersion,
        ResearchCatalogProvenance provenance,
        List<ResearchExerciseItem> items
) {
}
