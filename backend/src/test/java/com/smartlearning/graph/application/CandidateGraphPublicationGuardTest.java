package com.smartlearning.graph.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateGraphPublicationGuardTest {

    private final CandidateGraphPublicationGuard guard = new CandidateGraphPublicationGuard();

    @Test
    void cyclicCandidateGraphIsBlockedWithoutRemovingCandidateEdges() {
        CandidateGraphPublicationGuard.Outcome result = guard.assess(List.of(
                new CandidateGraphPublicationGuard.Edge(1L, 2L),
                new CandidateGraphPublicationGuard.Edge(2L, 3L),
                new CandidateGraphPublicationGuard.Edge(3L, 1L)
        ));

        assertThat(result.publicationStatus()).isEqualTo("BLOCKED_GRAPH_PUBLICATION");
        assertThat(result.cycleCount()).isGreaterThan(0);
    }

    @Test
    void selfLoopCandidateGraphIsBlocked() {
        CandidateGraphPublicationGuard.Outcome result = guard.assess(List.of(
                new CandidateGraphPublicationGuard.Edge(5L, 5L)
        ));

        assertThat(result.publicationStatus()).isEqualTo("BLOCKED_GRAPH_PUBLICATION");
        assertThat(result.selfLoopCount()).isEqualTo(1);
    }
}
