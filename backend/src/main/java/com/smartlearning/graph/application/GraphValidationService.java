package com.smartlearning.graph.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.GraphValidationIssue;
import com.smartlearning.graph.domain.GraphValidator;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphValidationIssueRepository;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GraphValidationService {

    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final GraphValidationIssueRepository issueRepository;
    private final GraphValidator graphValidator;
    private final ObjectMapper objectMapper;
    private final CourseAccessService courseAccessService;

    public GraphValidationService(
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationRepository relationRepository,
            KnowledgePointRepository knowledgePointRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            GraphValidationIssueRepository issueRepository,
            GraphValidator graphValidator,
            ObjectMapper objectMapper,
            CourseAccessService courseAccessService
    ) {
        this.graphVersionRepository = graphVersionRepository;
        this.relationRepository = relationRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.issueRepository = issueRepository;
        this.graphValidator = graphValidator;
        this.objectMapper = objectMapper;
        this.courseAccessService = courseAccessService;
    }

    @Transactional
    public ValidationOutcome validateForTeaching(long graphVersionId, CurrentUser user) {
        GraphVersion version = requireVersion(graphVersionId);
        courseAccessService.requireTeachingAccess(version.getCourseId(), user);
        return validate(graphVersionId);
    }

    public List<GraphApi.GraphValidationIssueResponse> listIssuesForTeaching(long graphVersionId, CurrentUser user) {
        GraphVersion version = requireVersion(graphVersionId);
        courseAccessService.requireTeachingAccess(version.getCourseId(), user);
        return listIssues(graphVersionId);
    }

    @Transactional
    public ValidationOutcome validate(long graphVersionId) {
        GraphVersion graphVersion = requireVersion(graphVersionId);
        graphVersion.beginValidation();

        List<KnowledgeRelation> relations = relationRepository
                .findByGraphVersionIdAndReviewStatusOrderByIdAsc(graphVersionId, RelationReviewStatus.APPROVED);
        Set<Long> pointIds = relations.stream()
                .flatMap(relation -> java.util.stream.Stream.of(
                        relation.getSourceKnowledgePointId(), relation.getTargetKnowledgePointId()
                ))
                .collect(Collectors.toSet());
        Map<Long, KnowledgePoint> points = knowledgePointRepository.findAllById(pointIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, point -> point));

        Map<Long, GraphValidator.Node> validatorNodes = new HashMap<>();
        Set<Long> exerciseCoveredPointIds = points.values().stream()
                .filter(point -> exerciseKnowledgeRepository.countByKnowledgePointId(point.getId()) > 0)
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());
        points.forEach((id, point) -> validatorNodes.put(id, new GraphValidator.Node(
                point.getId(), point.getCourseId(), point.getKnowledgeCode(), point.getKnowledgeName(), point.getStatus()
        )));

        GraphValidator.ValidationResult result = graphValidator.validate(new GraphValidator.ValidationInput(
                graphVersion.getCourseId(),
                relations.stream().map(this::toCandidate).toList(),
                validatorNodes,
                exerciseCoveredPointIds
        ));

        issueRepository.deleteByGraphVersionId(graphVersionId);
        List<GraphValidationIssue> savedIssues = new ArrayList<>();
        for (GraphValidator.Issue issue : result.issues()) {
            savedIssues.add(issueRepository.save(new GraphValidationIssue(
                    graphVersionId,
                    issue.relationId(),
                    issue.severity(),
                    issue.code(),
                    toJson(issue.nodes().stream().map(GraphValidator.Node::id).toList()),
                    toJson(issueDetail(issue))
            )));
        }
        if (result.hasErrors()) {
            graphVersion.markValidationFailed("graph validation produced " + result.errorCount() + " error issue(s)");
        } else {
            graphVersion.markReady();
        }
        return new ValidationOutcome(graphVersion, List.copyOf(savedIssues), result.hasErrors());
    }

    public List<GraphApi.GraphValidationIssueResponse> listIssues(long graphVersionId) {
        if (!graphVersionRepository.existsById(graphVersionId)) {
            throw new NotFoundException("graph version does not exist");
        }
        return issueRepository.findByGraphVersionIdOrderByIdAsc(graphVersionId).stream()
                .map(this::toIssueResponse)
                .toList();
    }

    private GraphValidator.RelationCandidate toCandidate(KnowledgeRelation relation) {
        return new GraphValidator.RelationCandidate(
                relation.getId(), relation.getSourceKnowledgePointId(), relation.getTargetKnowledgePointId(),
                relation.getRelationType(), relation.getRelationSource(), relation.getEvidenceCount()
        );
    }

    private Map<String, Object> issueDetail(GraphValidator.Issue issue) {
        Map<String, Object> detail = new LinkedHashMap<>(issue.details());
        detail.put("message", issue.message());
        detail.put("nodes", issue.nodes());
        return detail;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize graph validation issue", ex);
        }
    }

    private GraphVersion requireVersion(long graphVersionId) {
        return graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new NotFoundException("graph version does not exist"));
    }

    private GraphApi.GraphValidationIssueResponse toIssueResponse(GraphValidationIssue issue) {
        return new GraphApi.GraphValidationIssueResponse(
                issue.getId(), issue.getRelationId(), issue.getSeverity(), issue.getIssueCode(), issue.getNodeIdsJson(),
                issue.getDetailJson(), issue.getCreatedAt()
        );
    }

    public record ValidationOutcome(
            GraphVersion graphVersion,
            List<GraphValidationIssue> issues,
            boolean hasErrors
    ) {
    }
}
