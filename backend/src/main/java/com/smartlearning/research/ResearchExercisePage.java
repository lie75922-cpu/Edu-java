package com.smartlearning.research;

import java.util.List;

public record ResearchExercisePage(
        List<ResearchExerciseItem> items,
        long total,
        int page,
        int size
) {
}
