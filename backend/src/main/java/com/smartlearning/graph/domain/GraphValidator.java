package com.smartlearning.graph.domain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure graph-domain validation. Persistence and HTTP concerns deliberately stay outside this service.
 */
@Component
public class GraphValidator {

    public ValidationResult validate(ValidationInput input) {
        List<Issue> issues = new ArrayList<>();
        List<RelationCandidate> validForCycleDetection = new ArrayList<>();
        Set<Long> coverageChecked = new HashSet<>();

        Map<EdgeKey, List<RelationCandidate>> edges = new LinkedHashMap<>();
        for (RelationCandidate relation : input.relations()) {
            edges.computeIfAbsent(new EdgeKey(
                    relation.sourceKnowledgePointId(), relation.targetKnowledgePointId(), relation.relationType()
            ), ignored -> new ArrayList<>()).add(relation);

            Node source = input.nodesById().get(relation.sourceKnowledgePointId());
            Node target = input.nodesById().get(relation.targetKnowledgePointId());
            boolean validEndpoints = true;

            if (source == null) {
                issues.add(Issue.error("MISSING_SOURCE_NODE", relation.id(), List.of(),
                        "source knowledge point does not exist", Map.of("sourceKnowledgePointId", relation.sourceKnowledgePointId())));
                validEndpoints = false;
            }
            if (target == null) {
                issues.add(Issue.error("MISSING_TARGET_NODE", relation.id(), List.of(),
                        "target knowledge point does not exist", Map.of("targetKnowledgePointId", relation.targetKnowledgePointId())));
                validEndpoints = false;
            }
            if (source != null && source.courseId() != input.courseId()) {
                issues.add(Issue.error("CROSS_COURSE_EDGE", relation.id(), List.of(source),
                        "source knowledge point belongs to another course", Map.of("expectedCourseId", input.courseId(), "actualCourseId", source.courseId())));
                validEndpoints = false;
            }
            if (target != null && target.courseId() != input.courseId()) {
                issues.add(Issue.error("CROSS_COURSE_EDGE", relation.id(), List.of(target),
                        "target knowledge point belongs to another course", Map.of("expectedCourseId", input.courseId(), "actualCourseId", target.courseId())));
                validEndpoints = false;
            }
            if (source != null && !"ACTIVE".equals(source.status())) {
                issues.add(Issue.error("INACTIVE_NODE", relation.id(), List.of(source),
                        "source knowledge point is not active", Map.of("status", source.status())));
                validEndpoints = false;
            }
            if (target != null && !"ACTIVE".equals(target.status())) {
                issues.add(Issue.error("INACTIVE_NODE", relation.id(), List.of(target),
                        "target knowledge point is not active", Map.of("status", target.status())));
                validEndpoints = false;
            }
            if (relation.sourceKnowledgePointId().equals(relation.targetKnowledgePointId())) {
                List<Node> nodes = source == null ? List.of() : List.of(source);
                issues.add(Issue.error("SELF_LOOP", relation.id(), nodes,
                        "a prerequisite relation cannot point to the same knowledge point", Map.of("knowledgePointId", relation.sourceKnowledgePointId())));
                validEndpoints = false;
            }
            if (relation.evidenceCount() == 0) {
                issues.add(Issue.warning("NO_EVIDENCE_WARNING", relation.id(), nodesOf(source, target),
                        "relation has no linked evidence; manual provenance remains visible", Map.of("relationSource", relation.relationSource())));
            }
            checkExerciseCoverage(input, source, coverageChecked, issues);
            checkExerciseCoverage(input, target, coverageChecked, issues);

            if (validEndpoints) {
                validForCycleDetection.add(relation);
            }
        }

        for (Map.Entry<EdgeKey, List<RelationCandidate>> entry : edges.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (RelationCandidate relation : entry.getValue()) {
                    issues.add(Issue.error("DUPLICATE_EDGE", relation.id(), List.of(),
                            "duplicate prerequisite relation in the graph version", Map.of(
                                    "sourceKnowledgePointId", entry.getKey().sourceKnowledgePointId(),
                                    "targetKnowledgePointId", entry.getKey().targetKnowledgePointId(),
                                    "relationType", entry.getKey().relationType()
                            )));
                }
            }
        }

        issues.addAll(findCycles(validForCycleDetection, input.nodesById()));
        return new ValidationResult(List.copyOf(issues));
    }

    private void checkExerciseCoverage(
            ValidationInput input,
            Node node,
            Set<Long> checked,
            List<Issue> issues
    ) {
        if (node != null && checked.add(node.id()) && !input.exerciseCoveredPointIds().contains(node.id())) {
            issues.add(Issue.warning("NO_EXERCISE_COVERAGE_WARNING", null, List.of(node),
                    "knowledge point has no ExerciseUnit coverage", Map.of("knowledgePointId", node.id())));
        }
    }

    private List<Issue> findCycles(List<RelationCandidate> relations, Map<Long, Node> nodesById) {
        Map<Long, List<RelationCandidate>> adjacency = new HashMap<>();
        Set<Long> vertices = new LinkedHashSet<>();
        for (RelationCandidate relation : relations) {
            adjacency.computeIfAbsent(relation.sourceKnowledgePointId(), ignored -> new ArrayList<>()).add(relation);
            vertices.add(relation.sourceKnowledgePointId());
            vertices.add(relation.targetKnowledgePointId());
        }
        Comparator<RelationCandidate> relationOrder = Comparator
                .comparing(RelationCandidate::targetKnowledgePointId)
                .thenComparing(RelationCandidate::id);
        adjacency.values().forEach(list -> list.sort(relationOrder));

        Map<Long, VisitState> states = new HashMap<>();
        List<Long> stack = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        vertices.stream().sorted().forEach(nodeId -> {
            if (states.getOrDefault(nodeId, VisitState.UNVISITED) == VisitState.UNVISITED) {
                depthFirstFindCycles(nodeId, adjacency, nodesById, states, stack, issues);
            }
        });
        return issues;
    }

    private void depthFirstFindCycles(
            long nodeId,
            Map<Long, List<RelationCandidate>> adjacency,
            Map<Long, Node> nodesById,
            Map<Long, VisitState> states,
            List<Long> stack,
            List<Issue> issues
    ) {
        states.put(nodeId, VisitState.VISITING);
        stack.add(nodeId);
        for (RelationCandidate relation : adjacency.getOrDefault(nodeId, List.of())) {
            long target = relation.targetKnowledgePointId();
            VisitState targetState = states.getOrDefault(target, VisitState.UNVISITED);
            if (targetState == VisitState.UNVISITED) {
                depthFirstFindCycles(target, adjacency, nodesById, states, stack, issues);
            } else if (targetState == VisitState.VISITING) {
                int cycleStart = stack.indexOf(target);
                List<Long> cycleIds = new ArrayList<>(stack.subList(cycleStart, stack.size()));
                cycleIds.add(target);
                List<Node> cycleNodes = cycleIds.stream().map(nodesById::get).toList();
                issues.add(Issue.error("CYCLE", relation.id(), cycleNodes,
                        "directed prerequisite cycle detected", Map.of(
                                "representativeCycle", cycleIds,
                                "cycleNodeIds", cycleIds,
                                "cycleNodes", cycleNodes
                        )));
            }
        }
        stack.removeLast();
        states.put(nodeId, VisitState.VISITED);
    }

    private List<Node> nodesOf(Node source, Node target) {
        List<Node> nodes = new ArrayList<>(2);
        if (source != null) {
            nodes.add(source);
        }
        if (target != null && (source == null || !source.id().equals(target.id()))) {
            nodes.add(target);
        }
        return List.copyOf(nodes);
    }

    public record ValidationInput(
            long courseId,
            List<RelationCandidate> relations,
            Map<Long, Node> nodesById,
            Set<Long> exerciseCoveredPointIds
    ) {
        public ValidationInput {
            relations = List.copyOf(relations);
            nodesById = Map.copyOf(nodesById);
            exerciseCoveredPointIds = Set.copyOf(exerciseCoveredPointIds);
        }
    }

    public record RelationCandidate(
            Long id,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String relationType,
            String relationSource,
            int evidenceCount
    ) {
    }

    public record Node(Long id, long courseId, String code, String name, String status) {
    }

    public record ValidationResult(List<Issue> issues) {
        public boolean hasErrors() {
            return issues.stream().anyMatch(Issue::isError);
        }

        public long errorCount() {
            return issues.stream().filter(Issue::isError).count();
        }
    }

    public record Issue(
            String severity,
            String code,
            Long relationId,
            List<Node> nodes,
            String message,
            Map<String, Object> details
    ) {
        static Issue error(String code, Long relationId, List<Node> nodes, String message, Map<String, Object> details) {
            return new Issue("ERROR", code, relationId, List.copyOf(nodes), message, Map.copyOf(details));
        }

        static Issue warning(String code, Long relationId, List<Node> nodes, String message, Map<String, Object> details) {
            return new Issue("WARNING", code, relationId, List.copyOf(nodes), message, Map.copyOf(details));
        }

        public boolean isError() {
            return "ERROR".equals(severity);
        }
    }

    private record EdgeKey(Long sourceKnowledgePointId, Long targetKnowledgePointId, String relationType) {
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }
}
