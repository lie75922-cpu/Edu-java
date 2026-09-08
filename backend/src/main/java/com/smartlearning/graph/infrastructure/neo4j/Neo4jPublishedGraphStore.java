package com.smartlearning.graph.infrastructure.neo4j;

import com.smartlearning.graph.application.PublishedGraphStore;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class Neo4jPublishedGraphStore implements PublishedGraphStore {

    private static final String CONSTRAINT_NAME = "knowledge_point_projection_key_unique";
    private static final String KNOWLEDGE_POINT_CONSTRAINT = """
            CREATE CONSTRAINT knowledge_point_projection_key_unique IF NOT EXISTS
            FOR (node:KnowledgePoint) REQUIRE node.projectionKey IS UNIQUE
            """;

    private final Driver driver;

    public Neo4jPublishedGraphStore(Driver driver) {
        this.driver = driver;
    }

    @Override
    public void ensureConstraints() {
        try (Session session = driver.session()) {
            session.executeWrite(transaction -> {
                transaction.run(KNOWLEDGE_POINT_CONSTRAINT).consume();
                return null;
            });
        }
    }

    @Override
    public void project(ProjectionSnapshot snapshot) {
        try (Session session = driver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("""
                        UNWIND $nodes AS item
                        MERGE (node:KnowledgePoint {projectionKey: item.projectionKey})
                        SET node.businessId = item.businessId,
                            node.courseId = item.courseId,
                            node.graphVersionId = item.graphVersionId,
                            node.code = item.code,
                            node.name = item.name
                        """, Map.of("nodes", nodeParameters(snapshot))).consume();
                transaction.run("""
                        UNWIND $edges AS item
                        MATCH (source:KnowledgePoint {projectionKey: item.sourceProjectionKey})
                        MATCH (target:KnowledgePoint {projectionKey: item.targetProjectionKey})
                        MERGE (source)-[edge:PREREQUISITE {relationId: item.relationId}]->(target)
                        SET edge.graphVersionId = item.graphVersionId,
                            edge.relationSource = item.relationSource
                        """, Map.of("edges", edgeParameters(snapshot))).consume();
                return null;
            });
        }
    }

    @Override
    public void verifyProjection(long courseId, long graphVersionId, int expectedNodeCount, int expectedEdgeCount) {
        long nodeCount = count("""
                MATCH (node:KnowledgePoint {courseId: $courseId, graphVersionId: $graphVersionId})
                RETURN count(node) AS total
                """, Map.of("courseId", courseId, "graphVersionId", graphVersionId));
        long edgeCount = count("""
                MATCH (source:KnowledgePoint {courseId: $courseId, graphVersionId: $graphVersionId})
                      -[edge:PREREQUISITE {graphVersionId: $graphVersionId}]->
                      (target:KnowledgePoint {courseId: $courseId, graphVersionId: $graphVersionId})
                RETURN count(edge) AS total
                """, Map.of("courseId", courseId, "graphVersionId", graphVersionId));
        if (nodeCount != expectedNodeCount || edgeCount != expectedEdgeCount) {
            throw new IllegalStateException("Neo4j projection verification count mismatch");
        }
    }

    @Override
    public GraphSlice graph(long graphVersionId) {
        List<ProjectionNode> nodes = queryNodes("""
                MATCH (node:KnowledgePoint {graphVersionId: $graphVersionId})
                RETURN node.businessId AS businessId, node.code AS code, node.name AS name
                ORDER BY businessId
                """, Map.of("graphVersionId", graphVersionId));
        List<ProjectionEdge> edges = queryEdges("""
                MATCH (source:KnowledgePoint {graphVersionId: $graphVersionId})
                      -[edge:PREREQUISITE {graphVersionId: $graphVersionId}]->
                      (target:KnowledgePoint {graphVersionId: $graphVersionId})
                RETURN edge.relationId AS relationId, source.businessId AS sourceId,
                       target.businessId AS targetId, edge.relationSource AS relationSource
                ORDER BY relationId
                """, Map.of("graphVersionId", graphVersionId));
        return new GraphSlice(nodes, edges);
    }

    @Override
    public GraphSlice prerequisites(long graphVersionId, long knowledgePointId) {
        List<ProjectionNode> nodes = queryNodes("""
                MATCH (target:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $knowledgePointId})
                OPTIONAL MATCH (source:KnowledgePoint {graphVersionId: $graphVersionId})
                              -[:PREREQUISITE {graphVersionId: $graphVersionId}]->(target)
                WITH collect(target) + collect(source) AS values
                UNWIND values AS node
                WITH DISTINCT node WHERE node IS NOT NULL
                RETURN node.businessId AS businessId, node.code AS code, node.name AS name
                ORDER BY businessId
                """, Map.of("graphVersionId", graphVersionId, "knowledgePointId", knowledgePointId));
        List<ProjectionEdge> edges = queryEdges("""
                MATCH (source:KnowledgePoint {graphVersionId: $graphVersionId})
                      -[edge:PREREQUISITE {graphVersionId: $graphVersionId}]->
                      (target:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $knowledgePointId})
                RETURN edge.relationId AS relationId, source.businessId AS sourceId,
                       target.businessId AS targetId, edge.relationSource AS relationSource
                ORDER BY relationId
                """, Map.of("graphVersionId", graphVersionId, "knowledgePointId", knowledgePointId));
        return new GraphSlice(nodes, edges);
    }

    @Override
    public GraphSlice successors(long graphVersionId, long knowledgePointId) {
        List<ProjectionNode> nodes = queryNodes("""
                MATCH (source:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $knowledgePointId})
                OPTIONAL MATCH (source)-[:PREREQUISITE {graphVersionId: $graphVersionId}]
                              ->(target:KnowledgePoint {graphVersionId: $graphVersionId})
                WITH collect(source) + collect(target) AS values
                UNWIND values AS node
                WITH DISTINCT node WHERE node IS NOT NULL
                RETURN node.businessId AS businessId, node.code AS code, node.name AS name
                ORDER BY businessId
                """, Map.of("graphVersionId", graphVersionId, "knowledgePointId", knowledgePointId));
        List<ProjectionEdge> edges = queryEdges("""
                MATCH (source:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $knowledgePointId})
                      -[edge:PREREQUISITE {graphVersionId: $graphVersionId}]->
                      (target:KnowledgePoint {graphVersionId: $graphVersionId})
                RETURN edge.relationId AS relationId, source.businessId AS sourceId,
                       target.businessId AS targetId, edge.relationSource AS relationSource
                ORDER BY relationId
                """, Map.of("graphVersionId", graphVersionId, "knowledgePointId", knowledgePointId));
        return new GraphSlice(nodes, edges);
    }

    @Override
    public List<Long> path(long graphVersionId, long fromKnowledgePointId, long toKnowledgePointId) {
        try (Session session = driver.session()) {
            return session.executeRead(transaction -> {
                Result result = transaction.run("""
                        MATCH path = shortestPath(
                            (source:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $fromKnowledgePointId})
                            -[:PREREQUISITE*1..]->
                            (target:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $toKnowledgePointId})
                        )
                        WHERE all(edge IN relationships(path) WHERE edge.graphVersionId = $graphVersionId)
                        RETURN [node IN nodes(path) | node.businessId] AS nodeIds
                        LIMIT 1
                        """, Map.of(
                        "graphVersionId", graphVersionId,
                        "fromKnowledgePointId", fromKnowledgePointId,
                        "toKnowledgePointId", toKnowledgePointId
                ));
                if (!result.hasNext()) {
                    return List.of();
                }
                return result.single().get("nodeIds").asList(value -> value.asLong());
            });
        }
    }

    @Override
    public GraphSlice prerequisiteSubgraph(long graphVersionId, long knowledgePointId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(knowledgePointId);
        try (Session session = driver.session()) {
            List<Long> ancestorIds = session.executeRead(transaction -> {
                Result result = transaction.run("""
                        MATCH (ancestor:KnowledgePoint {graphVersionId: $graphVersionId})
                              -[:PREREQUISITE*1..]->
                              (target:KnowledgePoint {graphVersionId: $graphVersionId, businessId: $knowledgePointId})
                        RETURN DISTINCT ancestor.businessId AS businessId
                        """, Map.of("graphVersionId", graphVersionId, "knowledgePointId", knowledgePointId));
                return result.list(record -> record.get("businessId").asLong());
            });
            ids.addAll(ancestorIds);
        }
        List<Long> pointIds = ids.stream().sorted().toList();
        if (pointIds.isEmpty()) {
            return new GraphSlice(List.of(), List.of());
        }
        List<ProjectionNode> nodes = queryNodes("""
                MATCH (node:KnowledgePoint {graphVersionId: $graphVersionId})
                WHERE node.businessId IN $knowledgePointIds
                RETURN node.businessId AS businessId, node.code AS code, node.name AS name
                ORDER BY businessId
                """, Map.of("graphVersionId", graphVersionId, "knowledgePointIds", pointIds));
        if (nodes.stream().noneMatch(node -> node.businessId() == knowledgePointId)) {
            return new GraphSlice(List.of(), List.of());
        }
        List<ProjectionEdge> edges = queryEdges("""
                MATCH (source:KnowledgePoint {graphVersionId: $graphVersionId})
                      -[edge:PREREQUISITE {graphVersionId: $graphVersionId}]->
                      (target:KnowledgePoint {graphVersionId: $graphVersionId})
                WHERE source.businessId IN $knowledgePointIds AND target.businessId IN $knowledgePointIds
                RETURN edge.relationId AS relationId, source.businessId AS sourceId,
                       target.businessId AS targetId, edge.relationSource AS relationSource
                ORDER BY relationId
                """, Map.of("graphVersionId", graphVersionId, "knowledgePointIds", pointIds));
        return new GraphSlice(nodes, edges);
    }

    private List<Map<String, Object>> nodeParameters(ProjectionSnapshot snapshot) {
        return snapshot.nodes().stream().map(node -> Map.<String, Object>of(
                "projectionKey", node.projectionKey(snapshot.courseId(), snapshot.graphVersionId()),
                "businessId", node.businessId(),
                "courseId", snapshot.courseId(),
                "graphVersionId", snapshot.graphVersionId(),
                "code", node.knowledgeCode(),
                "name", node.knowledgeName()
        )).toList();
    }

    private List<Map<String, Object>> edgeParameters(ProjectionSnapshot snapshot) {
        return snapshot.edges().stream().map(edge -> Map.<String, Object>of(
                "relationId", edge.relationId(),
                "graphVersionId", snapshot.graphVersionId(),
                "relationSource", edge.relationSource(),
                "sourceProjectionKey", snapshot.courseId() + ":" + snapshot.graphVersionId() + ":" + edge.sourceKnowledgePointId(),
                "targetProjectionKey", snapshot.courseId() + ":" + snapshot.graphVersionId() + ":" + edge.targetKnowledgePointId()
        )).toList();
    }

    private long count(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(cypher, parameters).single().get("total").asLong());
        }
    }

    private List<ProjectionNode> queryNodes(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(cypher, parameters)
                    .list(record -> new ProjectionNode(
                            record.get("businessId").asLong(),
                            record.get("code").asString(),
                            record.get("name").asString()
                    )));
        }
    }

    private List<ProjectionEdge> queryEdges(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(cypher, parameters)
                    .list(record -> new ProjectionEdge(
                            record.get("relationId").asLong(),
                            record.get("sourceId").asLong(),
                            record.get("targetId").asLong(),
                            record.get("relationSource").asString()
                    )));
        }
    }

    public String constraintName() {
        return CONSTRAINT_NAME;
    }
}
