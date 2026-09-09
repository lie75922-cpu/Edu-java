package com.smartlearning.graph.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A fail-closed pre-publication guard for derived candidate input. It neither removes an edge nor
 * changes review state: reviewers retain the draft and must resolve the issue explicitly.
 */
@Component
public class CandidateGraphPublicationGuard {

    public Outcome assess(Collection<Edge> edges) {
        int selfLoops = (int) edges.stream().filter(edge -> edge.sourcePointId().equals(edge.targetPointId())).count();
        Map<Long, List<Long>> adjacency = new HashMap<>();
        Set<Long> vertices = new HashSet<>();
        for (Edge edge : edges) {
            if (!edge.sourcePointId().equals(edge.targetPointId())) {
                adjacency.computeIfAbsent(edge.sourcePointId(), ignored -> new ArrayList<>()).add(edge.targetPointId());
            }
            vertices.add(edge.sourcePointId());
            vertices.add(edge.targetPointId());
        }
        Map<Long, VisitState> states = new HashMap<>();
        int[] cycles = {0};
        for (Long vertex : vertices) {
            if (states.getOrDefault(vertex, VisitState.UNVISITED) == VisitState.UNVISITED) {
                findCycles(vertex, adjacency, states, cycles);
            }
        }
        String publicationStatus = selfLoops == 0 && cycles[0] == 0
                ? "NOT_PUBLISHED_REVIEW_REQUIRED"
                : "BLOCKED_GRAPH_PUBLICATION";
        return new Outcome(publicationStatus, selfLoops, cycles[0]);
    }

    private void findCycles(Long node, Map<Long, List<Long>> adjacency, Map<Long, VisitState> states, int[] cycles) {
        states.put(node, VisitState.VISITING);
        for (Long target : adjacency.getOrDefault(node, List.of())) {
            VisitState state = states.getOrDefault(target, VisitState.UNVISITED);
            if (state == VisitState.UNVISITED) {
                findCycles(target, adjacency, states, cycles);
            } else if (state == VisitState.VISITING) {
                cycles[0]++;
            }
        }
        states.put(node, VisitState.VISITED);
    }

    public record Edge(Long sourcePointId, Long targetPointId) {
    }

    public record Outcome(String publicationStatus, int selfLoopCount, int cycleCount) {
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }
}
