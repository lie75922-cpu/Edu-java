package com.smartlearning.graph.application;

import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Rebuilds only already-Published Neo4j read models from authoritative MySQL data. */
@Service
public class PublishedGraphReprojectionService {

    private final CourseRepository courseRepository;
    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final PublishedGraphStore publishedGraphStore;

    public PublishedGraphReprojectionService(
            CourseRepository courseRepository,
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgePointRepository knowledgePointRepository,
            PublishedGraphStore publishedGraphStore
    ) {
        this.courseRepository = courseRepository;
        this.graphVersionRepository = graphVersionRepository;
        this.relationRepository = relationRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.publishedGraphStore = publishedGraphStore;
    }

    @Transactional(readOnly = true)
    public List<ReprojectionResult> rebuildActivePublishedGraphs() {
        List<ReprojectionResult> results = new ArrayList<>();
        for (Course course : courseRepository.findAllByStatusOrderByCourseCodeAsc("ACTIVE")) {
            Long graphVersionId = course.getActiveGraphVersionId();
            if (graphVersionId == null) {
                continue;
            }
            GraphVersion version = graphVersionRepository.findById(graphVersionId)
                    .orElseThrow(() -> new ConflictException("active graph version does not exist for course " + course.getId()));
            if (version.getStatus() != GraphVersionStatus.PUBLISHED || !version.getCourseId().equals(course.getId())) {
                throw new ConflictException("active graph version is not a published snapshot for course " + course.getId());
            }
            List<KnowledgePoint> points = knowledgePointRepository
                    .findByCourseIdAndStatusOrderByKnowledgeCodeAsc(course.getId(), "ACTIVE");
            List<KnowledgeRelation> relations = relationRepository
                    .findByGraphVersionIdAndReviewStatusOrderByIdAsc(version.getId(), RelationReviewStatus.APPROVED);
            PublishedGraphStore.ProjectionSnapshot snapshot = new PublishedGraphStore.ProjectionSnapshot(
                    course.getId(),
                    version.getId(),
                    points.stream().map(point -> new PublishedGraphStore.ProjectionNode(
                            point.getId(), point.getKnowledgeCode(), point.getKnowledgeName()
                    )).toList(),
                    relations.stream().map(relation -> new PublishedGraphStore.ProjectionEdge(
                            relation.getId(), relation.getSourceKnowledgePointId(), relation.getTargetKnowledgePointId(),
                            relation.getRelationSource()
                    )).toList()
            );
            publishedGraphStore.ensureConstraints();
            publishedGraphStore.project(snapshot);
            publishedGraphStore.verifyProjection(course.getId(), version.getId(), snapshot.nodes().size(), snapshot.edges().size());
            results.add(new ReprojectionResult(course.getId(), version.getId(), snapshot.nodes().size(), snapshot.edges().size()));
        }
        return List.copyOf(results);
    }

    public record ReprojectionResult(long courseId, long graphVersionId, int nodeCount, int edgeCount) {
    }
}
