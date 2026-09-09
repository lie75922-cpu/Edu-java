package com.smartlearning.graph.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.KnowledgeRelationEvidenceLink;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceLinkRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.outbox.application.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphVersionService {

    private static final String PREREQUISITE = "PREREQUISITE";

    private final CourseRepository courseRepository;
    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final OutboxService outboxService;
    private final CourseAccessService courseAccessService;

    public GraphVersionService(
            CourseRepository courseRepository,
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository,
            KnowledgePointRepository knowledgePointRepository,
            OutboxService outboxService,
            CourseAccessService courseAccessService
    ) {
        this.courseRepository = courseRepository;
        this.graphVersionRepository = graphVersionRepository;
        this.relationRepository = relationRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.outboxService = outboxService;
        this.courseAccessService = courseAccessService;
    }

    @Transactional
    public GraphApi.GraphVersionResponse createForTeaching(GraphApi.CreateGraphVersionRequest request, CurrentUser user) {
        courseAccessService.requireTeachingAccess(request.courseId(), user);
        return create(request, user.id());
    }

    public List<GraphApi.GraphVersionResponse> listForTeaching(long courseId, CurrentUser user) {
        courseAccessService.requireTeachingAccess(courseId, user);
        return list(courseId);
    }

    public GraphApi.GraphVersionResponse getForTeaching(long graphVersionId, CurrentUser user) {
        GraphVersion version = requireTeachingVersion(graphVersionId, user);
        return toVersionResponse(version, requireCourse(version.getCourseId()));
    }

    public List<GraphApi.GraphRelationResponse> listRelationsForTeaching(long graphVersionId, CurrentUser user) {
        requireTeachingVersion(graphVersionId, user);
        return listRelations(graphVersionId);
    }

    @Transactional
    public GraphApi.GraphRelationResponse addManualRelationForTeaching(
            long graphVersionId,
            GraphApi.ManualRelationRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return addManualRelation(graphVersionId, request, user.id());
    }

    @Transactional
    public GraphApi.GraphRelationResponse reviewRelationForTeaching(
            long graphVersionId,
            long relationId,
            GraphApi.RelationReviewRequest request,
            CurrentUser user
    ) {
        requireTeachingVersion(graphVersionId, user);
        return reviewRelation(graphVersionId, relationId, request);
    }

    @Transactional
    public void rejectRelationForTeaching(long graphVersionId, long relationId, CurrentUser user) {
        requireTeachingVersion(graphVersionId, user);
        rejectRelation(graphVersionId, relationId);
    }

    @Transactional
    public GraphApi.GraphVersionResponse requestPublishForTeaching(long graphVersionId, CurrentUser user) {
        requireTeachingVersion(graphVersionId, user);
        return requestPublish(graphVersionId);
    }

    @Transactional
    public GraphApi.GraphVersionResponse retryProjectionForTeaching(long graphVersionId, CurrentUser user) {
        requireTeachingVersion(graphVersionId, user);
        return retryProjection(graphVersionId);
    }

    @Transactional
    public GraphApi.GraphVersionResponse create(GraphApi.CreateGraphVersionRequest request, long createdBy) {
        Course course = requireCourse(request.courseId());
        int nextVersionNo = graphVersionRepository.findTopByCourseIdOrderByVersionNoDesc(course.getId())
                .map(version -> version.getVersionNo() + 1)
                .orElse(1);
        GraphVersion target = graphVersionRepository.save(new GraphVersion(
                course.getId(), nextVersionNo, request.description(), createdBy
        ));
        if (request.copyActive()) {
            copyActiveSnapshot(course, target, createdBy);
        }
        return toVersionResponse(target, course);
    }

    public List<GraphApi.GraphVersionResponse> list(long courseId) {
        Course course = requireCourse(courseId);
        return graphVersionRepository.findByCourseIdOrderByVersionNoDesc(courseId).stream()
                .map(version -> toVersionResponse(version, course))
                .toList();
    }

    public GraphApi.GraphVersionResponse get(long graphVersionId) {
        GraphVersion version = requireVersion(graphVersionId);
        return toVersionResponse(version, requireCourse(version.getCourseId()));
    }

    @Transactional
    public GraphApi.GraphRelationResponse addManualRelation(
            long graphVersionId,
            GraphApi.ManualRelationRequest request,
            long createdBy
    ) {
        GraphVersion version = requireEditableVersion(graphVersionId);
        KnowledgePoint source = requirePoint(request.sourceKnowledgePointId());
        KnowledgePoint target = requirePoint(request.targetKnowledgePointId());
        if (!source.getCourseId().equals(version.getCourseId()) || !target.getCourseId().equals(version.getCourseId())) {
            throw new ConflictException("manual graph relation endpoints must belong to the graph version course");
        }
        relationRepository.findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
                graphVersionId, source.getId(), target.getId(), PREREQUISITE
        ).ifPresent(existing -> {
            throw new ConflictException("a relation for this knowledge point pair already exists in the draft");
        });
        KnowledgeRelation relation = relationRepository.save(new KnowledgeRelation(
                graphVersionId,
                source.getId(),
                target.getId(),
                PREREQUISITE,
                "MANUAL",
                request.confidence(),
                0,
                RelationReviewStatus.APPROVED,
                createdBy
        ));
        return toRelationResponse(relation);
    }

    @Transactional
    public GraphApi.GraphRelationResponse reviewRelation(
            long graphVersionId,
            long relationId,
            GraphApi.RelationReviewRequest request
    ) {
        requireEditableVersion(graphVersionId);
        KnowledgeRelation relation = requireRelation(graphVersionId, relationId);
        relation.review(RelationReviewStatus.valueOf(request.reviewStatus()));
        return toRelationResponse(relation);
    }

    @Transactional
    public void rejectRelation(long graphVersionId, long relationId) {
        requireEditableVersion(graphVersionId);
        requireRelation(graphVersionId, relationId).review(RelationReviewStatus.REJECTED);
    }

    public List<GraphApi.GraphRelationResponse> listRelations(long graphVersionId) {
        requireVersion(graphVersionId);
        return relationRepository.findByGraphVersionIdOrderByIdAsc(graphVersionId).stream()
                .map(this::toRelationResponse)
                .toList();
    }

    @Transactional
    public GraphApi.GraphVersionResponse requestPublish(long graphVersionId) {
        GraphVersion version = requireVersion(graphVersionId);
        if (version.getStatus() != GraphVersionStatus.READY) {
            throw new ConflictException("only a ready graph version can be queued for projection");
        }
        version.beginPublishing();
        outboxService.enqueueGraphRebuild(version.getId(), version.getCourseId());
        return toVersionResponse(version, requireCourse(version.getCourseId()));
    }

    @Transactional
    public GraphApi.GraphVersionResponse retryProjection(long graphVersionId) {
        GraphVersion version = requireVersion(graphVersionId);
        version.retryProjection();
        outboxService.enqueueGraphRebuild(version.getId(), version.getCourseId());
        return toVersionResponse(version, requireCourse(version.getCourseId()));
    }

    GraphVersion requireEditableVersion(long graphVersionId) {
        GraphVersion version = requireVersion(graphVersionId);
        version.reopenForEditing();
        return version;
    }

    GraphVersion requireVersion(long graphVersionId) {
        return graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
    }

    private GraphVersion requireTeachingVersion(long graphVersionId, CurrentUser user) {
        GraphVersion version = requireVersion(graphVersionId);
        courseAccessService.requireTeachingAccess(version.getCourseId(), user);
        return version;
    }

    private void copyActiveSnapshot(Course course, GraphVersion target, long copiedBy) {
        Long activeGraphVersionId = course.getActiveGraphVersionId();
        if (activeGraphVersionId == null) {
            throw new ConflictException("course has no active published graph to copy");
        }
        GraphVersion sourceVersion = requireVersion(activeGraphVersionId);
        if (sourceVersion.getStatus() != GraphVersionStatus.PUBLISHED) {
            throw new ConflictException("course active graph version is not published");
        }
        List<KnowledgeRelation> sourceRelations = relationRepository.findByGraphVersionIdOrderByIdAsc(sourceVersion.getId());
        Map<Long, Long> copiedRelationIds = new HashMap<>();
        for (KnowledgeRelation sourceRelation : sourceRelations) {
            KnowledgeRelation copiedRelation = relationRepository.save(sourceRelation.copyForGraphVersion(target.getId(), copiedBy));
            copiedRelationIds.put(sourceRelation.getId(), copiedRelation.getId());
        }
        if (!copiedRelationIds.isEmpty()) {
            evidenceLinkRepository.findByRelationIdInOrderByIdAsc(copiedRelationIds.keySet()).forEach(link ->
                    evidenceLinkRepository.save(new KnowledgeRelationEvidenceLink(
                            copiedRelationIds.get(link.getRelationId()), link.getEvidenceId()
                    ))
            );
        }
    }

    private Course requireCourse(long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("course does not exist"));
    }

    private KnowledgePoint requirePoint(long pointId) {
        return knowledgePointRepository.findById(pointId)
                .orElseThrow(() -> new NotFoundException("knowledge point does not exist"));
    }

    private KnowledgeRelation requireRelation(long graphVersionId, long relationId) {
        KnowledgeRelation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new NotFoundException("knowledge relation does not exist"));
        if (!relation.getGraphVersionId().equals(graphVersionId)) {
            throw new ConflictException("knowledge relation does not belong to this graph version");
        }
        return relation;
    }

    private GraphApi.GraphVersionResponse toVersionResponse(GraphVersion version, Course course) {
        return new GraphApi.GraphVersionResponse(
                version.getId(), version.getCourseId(), version.getVersionNo(), version.getStatus().name(),
                version.getDescription(), version.getCreatedBy(), version.getCreatedAt(), version.getValidatedAt(),
                version.getPublishedAt(), version.getFailureReason(), version.getId().equals(course.getActiveGraphVersionId())
        );
    }

    private GraphApi.GraphRelationResponse toRelationResponse(KnowledgeRelation relation) {
        KnowledgePoint source = requirePoint(relation.getSourceKnowledgePointId());
        KnowledgePoint target = requirePoint(relation.getTargetKnowledgePointId());
        return new GraphApi.GraphRelationResponse(
                relation.getId(), relation.getGraphVersionId(), source.getId(), source.getKnowledgeCode(), source.getKnowledgeName(),
                target.getId(), target.getKnowledgeCode(), target.getKnowledgeName(), relation.getRelationType(),
                relation.getRelationSource(), relation.getCandidateInputId(), relation.getCandidatePolicyVersion(),
                relation.getCandidateStatus(), relation.getPublishedGraphStatus(),
                relation.getConfidence(), relation.getEvidenceCount(), relation.getReviewStatus().name(),
                relation.getCreatedBy(), relation.getCreatedAt()
        );
    }
}
