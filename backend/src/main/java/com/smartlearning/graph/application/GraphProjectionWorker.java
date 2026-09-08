package com.smartlearning.graph.application;

import com.smartlearning.common.exception.NotFoundException;
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
import com.smartlearning.outbox.domain.OutboxEvent;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Processes only graph projection events. MASTERY_UPDATE_REQUEST remains deliberately untouched.
 */
@Component
public class GraphProjectionWorker {

    private static final String GRAPH_REBUILD_REQUEST = "GRAPH_REBUILD_REQUEST";

    private final OutboxEventRepository outboxEventRepository;
    private final GraphVersionRepository graphVersionRepository;
    private final CourseRepository courseRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final PublishedGraphStore publishedGraphStore;

    public GraphProjectionWorker(
            OutboxEventRepository outboxEventRepository,
            GraphVersionRepository graphVersionRepository,
            CourseRepository courseRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgePointRepository knowledgePointRepository,
            PublishedGraphStore publishedGraphStore
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.graphVersionRepository = graphVersionRepository;
        this.courseRepository = courseRepository;
        this.relationRepository = relationRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.publishedGraphStore = publishedGraphStore;
    }

    @Scheduled(
            initialDelayString = "${app.graph-projection.initial-delay-ms:30000}",
            fixedDelayString = "${app.graph-projection.poll-interval-ms:5000}"
    )
    @Transactional
    public void pollGraphRebuildRequests() {
        processNext();
    }

    @Transactional
    public boolean processNext() {
        OutboxEvent event = outboxEventRepository
                .findFirstByEventTypeAndStatusOrderByIdAsc(GRAPH_REBUILD_REQUEST, "PENDING")
                .orElse(null);
        if (event == null) {
            return false;
        }
        GraphVersion version = null;
        try {
            long graphVersionId = Long.parseLong(event.getAggregateId());
            version = graphVersionRepository.findById(graphVersionId)
                    .orElseThrow(() -> new NotFoundException("graph version for projection event does not exist"));
            if (version.getStatus() != GraphVersionStatus.PUBLISHING) {
                throw new IllegalStateException("graph version is not in PUBLISHING state");
            }
            Course course = courseRepository.findById(version.getCourseId())
                    .orElseThrow(() -> new NotFoundException("course for graph version does not exist"));
            List<KnowledgePoint> points = knowledgePointRepository
                    .findByCourseIdAndStatusOrderByKnowledgeCodeAsc(course.getId(), "ACTIVE");
            List<KnowledgeRelation> approvedRelations = relationRepository
                    .findByGraphVersionIdAndReviewStatusOrderByIdAsc(version.getId(), RelationReviewStatus.APPROVED);
            PublishedGraphStore.ProjectionSnapshot snapshot = new PublishedGraphStore.ProjectionSnapshot(
                    course.getId(),
                    version.getId(),
                    points.stream().map(point -> new PublishedGraphStore.ProjectionNode(
                            point.getId(), point.getKnowledgeCode(), point.getKnowledgeName()
                    )).toList(),
                    approvedRelations.stream().map(relation -> new PublishedGraphStore.ProjectionEdge(
                            relation.getId(), relation.getSourceKnowledgePointId(), relation.getTargetKnowledgePointId(),
                            relation.getRelationSource()
                    )).toList()
            );
            publishedGraphStore.ensureConstraints();
            publishedGraphStore.project(snapshot);
            publishedGraphStore.verifyProjection(
                    snapshot.courseId(), snapshot.graphVersionId(), snapshot.nodes().size(), snapshot.edges().size()
            );

            Long previousActiveGraphVersionId = course.getActiveGraphVersionId();
            version.markPublished();
            course.activateGraphVersion(version.getId());
            if (previousActiveGraphVersionId != null && !previousActiveGraphVersionId.equals(version.getId())) {
                graphVersionRepository.findById(previousActiveGraphVersionId).ifPresent(previous -> {
                    if (previous.getStatus() == GraphVersionStatus.PUBLISHED) {
                        previous.archive();
                    }
                });
            }
            event.markDone();
        } catch (Exception ex) {
            if (version != null && version.getStatus() == GraphVersionStatus.PUBLISHING) {
                version.markProjectionFailed(safeFailureReason(ex));
            }
            event.markFailed(safeFailureReason(ex));
        }
        return true;
    }

    private String safeFailureReason(Exception exception) {
        String message = exception.getMessage();
        String reason = exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() > 1500 ? reason.substring(0, 1500) : reason;
    }
}
