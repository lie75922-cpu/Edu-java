package com.smartlearning.catalog.api;

import java.time.Instant;
import java.util.List;

public final class DataGovernanceApi {

    private DataGovernanceApi() {
    }

    public record OverviewResponse(
            long catalogImportRunCount,
            long catalogConflictCount,
            long rawEvidenceCount,
            long derivedCandidateRelationCount,
            long publishedGraphVersionCount,
            long publishedGraphRelationCount
    ) {
    }

    public record ImportRunResponse(
            Long id,
            String mode,
            String sourceName,
            String exportFormatVersion,
            String inputPath,
            Long inputSizeBytes,
            String inputEncoding,
            String inputColumnsJson,
            Integer sourceRecordCount,
            String status,
            String summaryJson,
            int quarantineCount,
            long conflictCount,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record ImportRunsResponse(List<ImportRunResponse> importRuns) {
        public ImportRunsResponse {
            importRuns = List.copyOf(importRuns);
        }
    }
}
