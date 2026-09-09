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
        Map<Long, Integer> discoveryIndex = new HashMap<>();
        Map<Long, Integer> lowLink = new HashMap<>();
        List<Long> stack = new ArrayList<>();
        Set<Long> onStack = new HashSet<>();
        int[] nextIndex = {0};
        int[] cyclicStronglyConnectedComponents = {0};
        for (Long vertex : vertices) {
            if (!discoveryIndex.containsKey(vertex)) {
                findCyclicStronglyConnectedComponents(
                        vertex, adjacency, discoveryIndex, lowLink, stack, onStack, nextIndex, cyclicStronglyConnectedComponents
                );
            }
        }
        String publicationStatus = selfLoops == 0 && cyclicStronglyConnectedComponents[0] == 0
                ? "NOT_PUBLISHED_REVIEW_REQUIRED"
                : "BLOCKED_GRAPH_PUBLICATION";
        return new Outcome(publicationStatus, selfLoops, cyclicStronglyConnectedComponents[0]);
    }

    private void findCyclicStronglyConnectedComponents(
            Long node,
            Map<Long, List<Long>> adjacency,
            Map<Long, Integer> discoveryIndex,
            Map<Long, Integer> lowLink,
            List<Long> stack,
            Set<Long> onStack,
            int[] nextIndex,
            int[] cyclicStronglyConnectedComponents
    ) {
        discoveryIndex.put(node, nextIndex[0]);
        lowLink.put(node, nextIndex[0]++);
        stack.add(node);
        onStack.add(node);
        for (Long target : adjacency.getOrDefault(node, List.of())) {
            if (!discoveryIndex.containsKey(target)) {
                findCyclicStronglyConnectedComponents(
                        target, adjacency, discoveryIndex, lowLink, stack, onStack, nextIndex, cyclicStronglyConnectedComponents
                );
                lowLink.put(node, Math.min(lowLink.get(node), lowLink.get(target)));
            } else if (onStack.contains(target)) {
                lowLink.put(node, Math.min(lowLink.get(node), discoveryIndex.get(target)));
            }
        }
        if (!lowLink.get(node).equals(discoveryIndex.get(node))) {
            return;
        }
        List<Long> component = new ArrayList<>();
        Long member;
        do {
            member = stack.removeLast();
            onStack.remove(member);
            component.add(member);
        } while (!member.equals(node));
        if (component.size() > 1) {
            cyclicStronglyConnectedComponents[0]++;
        }
    }

    public record Edge(Long sourcePointId, Long targetPointId) {
    }

    public record Outcome(String publicationStatus, int selfLoopCount, int cycleCount) {
    }

}
