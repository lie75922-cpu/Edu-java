package com.smartlearning.graph.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraphValidatorTest {

    private final GraphValidator validator = new GraphValidator();

    @Test
    void rejectsSelfLoop() {
        GraphValidator.ValidationResult result = validate(
                List.of(relation(11L, 1L, 1L, 1)),
                Map.of(1L, node(1L, 1L, "ACTIVE")),
                Set.of(1L)
        );

        assertThat(codes(result)).contains("SELF_LOOP");
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void returnsRepresentativeNodesForTwoNodeCycle() {
        GraphValidator.ValidationResult result = validate(
                List.of(relation(11L, 1L, 2L, 1), relation(12L, 2L, 1L, 1)),
                Map.of(1L, node(1L, 1L, "ACTIVE"), 2L, node(2L, 1L, "ACTIVE")),
                Set.of(1L, 2L)
        );

        GraphValidator.Issue cycle = result.issues().stream().filter(issue -> "CYCLE".equals(issue.code())).findFirst().orElseThrow();
        assertThat(cycle.nodes()).extracting(GraphValidator.Node::id).containsExactly(1L, 2L, 1L);
        assertThat(cycle.nodes()).extracting(GraphValidator.Node::code).containsExactly("KP-1", "KP-2", "KP-1");
    }

    @Test
    void returnsRepresentativeNodesForThreeNodeCycle() {
        GraphValidator.ValidationResult result = validate(
                List.of(relation(11L, 1L, 2L, 1), relation(12L, 2L, 3L, 1), relation(13L, 3L, 1L, 1)),
                Map.of(
                        1L, node(1L, 1L, "ACTIVE"),
                        2L, node(2L, 1L, "ACTIVE"),
                        3L, node(3L, 1L, "ACTIVE")
                ),
                Set.of(1L, 2L, 3L)
        );

        GraphValidator.Issue cycle = result.issues().stream().filter(issue -> "CYCLE".equals(issue.code())).findFirst().orElseThrow();
        assertThat(cycle.nodes()).extracting(GraphValidator.Node::id).containsExactly(1L, 2L, 3L, 1L);
    }

    @Test
    void identifiesDuplicateCrossCourseMissingAndInactiveRelations() {
        GraphValidator.ValidationResult result = validate(
                List.of(
                        relation(11L, 1L, 2L, 1),
                        relation(12L, 1L, 2L, 1),
                        relation(13L, 1L, 3L, 1),
                        relation(14L, 1L, 4L, 1),
                        relation(15L, 5L, 1L, 1)
                ),
                Map.of(
                        1L, node(1L, 1L, "ACTIVE"),
                        2L, node(2L, 1L, "INACTIVE"),
                        3L, node(3L, 2L, "ACTIVE"),
                        5L, node(5L, 1L, "ACTIVE")
                ),
                Set.of(1L, 2L, 3L, 5L)
        );

        assertThat(codes(result)).contains("DUPLICATE_EDGE", "CROSS_COURSE_EDGE", "MISSING_TARGET_NODE", "INACTIVE_NODE");
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void reportsEvidenceAndExerciseCoverageWarningsWithoutBlockingValidDag() {
        GraphValidator.ValidationResult result = validate(
                List.of(relation(11L, 1L, 2L, 0)),
                Map.of(1L, node(1L, 1L, "ACTIVE"), 2L, node(2L, 1L, "ACTIVE")),
                Set.of(1L)
        );

        assertThat(codes(result)).contains("NO_EVIDENCE_WARNING", "NO_EXERCISE_COVERAGE_WARNING");
        assertThat(result.hasErrors()).isFalse();
    }

    private GraphValidator.ValidationResult validate(
            List<GraphValidator.RelationCandidate> relations,
            Map<Long, GraphValidator.Node> nodes,
            Set<Long> coveredPoints
    ) {
        return validator.validate(new GraphValidator.ValidationInput(1L, relations, nodes, coveredPoints));
    }

    private GraphValidator.RelationCandidate relation(long id, long source, long target, int evidenceCount) {
        return new GraphValidator.RelationCandidate(id, source, target, "PREREQUISITE", "EVIDENCE", evidenceCount);
    }

    private GraphValidator.Node node(long id, long courseId, String status) {
        return new GraphValidator.Node(id, courseId, "KP-" + id, "Knowledge " + id, status);
    }

    private List<String> codes(GraphValidator.ValidationResult result) {
        return result.issues().stream().map(GraphValidator.Issue::code).toList();
    }
}
