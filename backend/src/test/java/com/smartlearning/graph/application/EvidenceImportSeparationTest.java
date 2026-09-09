package com.smartlearning.graph.application;

import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.EvidenceResolutionStatus;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceConflictRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceImportRunRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceLinkRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceImportSeparationTest {

    @Test
    void rawEvidenceImportDoesNotImplicitlyCreateDerivedCandidateRelations() {
        GraphVersion version = new GraphVersion(1L, 1, "fixture", 9L);
        ReflectionTestUtils.setField(version, "id", 10L);
        GraphVersionRepository graphVersionRepository = mock(GraphVersionRepository.class);
        KnowledgeRelationEvidenceRepository evidenceRepository = mock(KnowledgeRelationEvidenceRepository.class);
        KnowledgeRelationRepository relationRepository = mock(KnowledgeRelationRepository.class);
        EvidenceResolutionResolver resolver = mock(EvidenceResolutionResolver.class);
        when(graphVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(evidenceRepository.findByCourseIdAndSourceTypeAndExternalEvidenceId(1L, "JUNYI_RAW_PREREQUISITE", "raw-1"))
                .thenReturn(Optional.empty());
        when(resolver.resolve(1L, "JUNYI_RAW_PREREQUISITE", "exercise-a", "exercise-b"))
                .thenReturn(new KnowledgeRelationEvidence.ResolutionState(
                        EvidenceResolutionStatus.RESOLVED, null, null, 11L, 12L, 21L, 22L
                ));

        EvidenceImportService service = new EvidenceImportService(
                graphVersionRepository,
                evidenceRepository,
                relationRepository,
                mock(KnowledgeRelationEvidenceLinkRepository.class),
                mock(KnowledgeRelationEvidenceImportRunRepository.class),
                mock(KnowledgeRelationEvidenceConflictRepository.class),
                resolver,
                mock(KnowledgePointRepository.class),
                new CandidateGraphPublicationGuard(),
                new ObjectMapper(),
                mock(CourseAccessService.class)
        );

        GraphApi.EvidenceImportResult result = service.dryRun(10L, new GraphApi.EvidenceImportRequest(List.of(
                new GraphApi.RawPrerequisiteEvidenceRequest("raw-1", "exercise-a", "exercise-b", java.util.Map.of("source", "fixture"))
        )), 9L);

        assertThat(result.createdEvidence()).isEqualTo(1);
        assertThat(result.candidateRelationsCreated()).isZero();
        assertThat(result.candidateRelationsAggregated()).isZero();
        verify(relationRepository, never()).save(any());
    }
}
