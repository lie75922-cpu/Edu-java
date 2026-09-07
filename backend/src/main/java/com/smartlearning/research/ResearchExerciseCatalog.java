package com.smartlearning.research;

import java.util.List;

public record ResearchExerciseCatalog(
        int schemaVersion,
        String source,
        List<ResearchExerciseItem> items
) {
}
