package com.smartlearning.graph.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceReresolutionDomainTest {

    @Test
    void applyingDerivedResolutionNeverChangesImmutableRawEvidenceIdentityOrPayload() {
        KnowledgeRelationEvidence evidence = new KnowledgeRelationEvidence(
                1L, "JUNYI_RAW_PREREQUISITE", "raw-1", "exercise-source", "exercise-target",
                null, null, null, null, "{\"raw\":true}", EvidenceResolutionStatus.UNMAPPED_TARGET,
                "UNMAPPED_TARGET", "target has no mapping"
        );

        evidence.applyResolution(new KnowledgeRelationEvidence.ResolutionState(
                EvidenceResolutionStatus.RESOLVED, null, null, 11L, 12L, 101L, 102L
        ));

        assertThat(evidence.getCourseId()).isEqualTo(1L);
        assertThat(evidence.getSourceType()).isEqualTo("JUNYI_RAW_PREREQUISITE");
        assertThat(evidence.getExternalEvidenceId()).isEqualTo("raw-1");
        assertThat(evidence.getSourceExternalId()).isEqualTo("exercise-source");
        assertThat(evidence.getTargetExternalId()).isEqualTo("exercise-target");
        assertThat(evidence.getRawPayloadJson()).isEqualTo("{\"raw\":true}");
        assertThat(evidence.resolutionState()).isEqualTo(new KnowledgeRelationEvidence.ResolutionState(
                EvidenceResolutionStatus.RESOLVED, null, null, 11L, 12L, 101L, 102L
        ));
    }

    @Test
    void evidenceGeneratedCandidateWithNoLinksIsExplicitlyRejected() {
        KnowledgeRelation relation = new KnowledgeRelation(
                1L, 101L, 102L, "PREREQUISITE", "EVIDENCE", BigDecimal.ONE,
                1, RelationReviewStatus.CANDIDATE, 9L
        );

        relation.reconcileEvidenceCount(0);

        assertThat(relation.getEvidenceCount()).isZero();
        assertThat(relation.getReviewStatus()).isEqualTo(RelationReviewStatus.REJECTED);
    }
}
