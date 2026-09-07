package com.smartlearning.research;

import java.util.List;

public record ResearchExerciseItem(
        int recordNumber,
        String externalId,
        String displayName,
        String topic,
        String area,
        boolean live,
        List<String> prerequisites,
        boolean duplicateExternalId
) {
}
