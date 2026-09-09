package com.smartlearning.catalog.application;

import com.smartlearning.catalog.api.DataGovernanceApi;
import com.smartlearning.catalog.domain.SeedImportRun;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportConflictRepository;
import com.smartlearning.catalog.infrastructure.persistence.SeedImportRunRepository;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read-only administrator view over persisted import and graph-governance state. */
@Service
public class DataGovernanceService {

    private static final String DERIVED_POLICY = "DERIVED_POLICY";

    private final SeedImportRunRepository runRepository;
    private final SeedImportConflictRepository conflictRepository;
    private final KnowledgeRelationEvidenceRepository evidenceRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final GraphVersionRepository graphVersionRepository;

    public DataGovernanceService(
            SeedImportRunRepository runRepository,
            SeedImportConflictRepository conflictRepository,
            KnowledgeRelationEvidenceRepository evidenceRepository,
            KnowledgeRelationRepository relationRepository,
            GraphVersionRepository graphVersionRepository
    ) {
        this.runRepository = runRepository;
        this.conflictRepository = conflictRepository;
        this.evidenceRepository = evidenceRepository;
        this.relationRepository = relationRepository;
        this.graphVersionRepository = graphVersionRepository;
    }

    public DataGovernanceApi.OverviewResponse overview() {
        List<SeedImportRun> runs = runRepository.findAllByOrderByIdDesc();
        long conflicts = runs.stream().mapToLong(run -> conflictRepository.countByImportRunId(run.getId())).sum();
        return new DataGovernanceApi.OverviewResponse(
                runs.size(),
                conflicts,
                evidenceRepository.count(),
                relationRepository.countByRelationSource(DERIVED_POLICY),
                graphVersionRepository.countByStatus(GraphVersionStatus.PUBLISHED),
                relationRepository.countByGraphVersionStatus(GraphVersionStatus.PUBLISHED)
        );
    }

    public DataGovernanceApi.ImportRunsResponse importRuns() {
        return new DataGovernanceApi.ImportRunsResponse(runRepository.findAllByOrderByIdDesc().stream()
                .map(this::toImportRunResponse)
                .toList());
    }

    private DataGovernanceApi.ImportRunResponse toImportRunResponse(SeedImportRun run) {
        return new DataGovernanceApi.ImportRunResponse(
                run.getId(), run.getMode(), run.getSourceName(), run.getExportFormatVersion(), run.getInputPath(),
                run.getInputSizeBytes(), run.getInputEncoding(), run.getInputColumnsJson(), run.getSourceRecordCount(),
                run.getStatus(), run.getSummaryJson(), run.getQuarantineCount(),
                conflictRepository.countByImportRunId(run.getId()), run.getCreatedAt(), run.getCompletedAt()
        );
    }
}
