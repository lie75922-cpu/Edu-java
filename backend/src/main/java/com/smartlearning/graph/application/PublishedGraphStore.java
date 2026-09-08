package com.smartlearning.graph.application;

import java.util.List;

/** Read-model boundary: implementations must only expose business IDs, never Neo4j internal node IDs. */
public interface PublishedGraphStore {

    void ensureConstraints();

    void project(ProjectionSnapshot snapshot);

    void verifyProjection(long courseId, long graphVersionId, int expectedNodeCount, int expectedEdgeCount);

    GraphSlice graph(long graphVersionId);

    GraphSlice prerequisites(long graphVersionId, long knowledgePointId);

    GraphSlice successors(long graphVersionId, long knowledgePointId);

    List<Long> path(long graphVersionId, long fromKnowledgePointId, long toKnowledgePointId);

    GraphSlice prerequisiteSubgraph(long graphVersionId, long knowledgePointId);

    record ProjectionSnapshot(long courseId, long graphVersionId, List<ProjectionNode> nodes, List<ProjectionEdge> edges) {
        public ProjectionSnapshot {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    record ProjectionNode(long businessId, String knowledgeCode, String knowledgeName) {
        public String projectionKey(long courseId, long graphVersionId) {
            return courseId + ":" + graphVersionId + ":" + businessId;
        }
    }

    record ProjectionEdge(long relationId, long sourceKnowledgePointId, long targetKnowledgePointId, String relationSource) {
    }

    record GraphSlice(List<ProjectionNode> nodes, List<ProjectionEdge> edges) {
        public GraphSlice {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }
}
