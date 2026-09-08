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
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphQueryService {

    private final CourseRepository courseRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final GraphVersionRepository graphVersionRepository;
    private final CourseAccessService courseAccessService;
    private final PublishedGraphStore publishedGraphStore;

    public GraphQueryService(
            CourseRepository courseRepository,
            KnowledgePointRepository knowledgePointRepository,
            GraphVersionRepository graphVersionRepository,
            CourseAccessService courseAccessService,
            PublishedGraphStore publishedGraphStore
    ) {
        this.courseRepository = courseRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.graphVersionRepository = graphVersionRepository;
        this.courseAccessService = courseAccessService;
        this.publishedGraphStore = publishedGraphStore;
    }

    public GraphApi.GraphViewResponse graph(long courseId, CurrentUser user) {
        long graphVersionId = requireActiveGraphVersion(courseId, user);
        return toView(graphVersionId, publishedGraphStore.graph(graphVersionId));
    }

    public GraphApi.GraphViewResponse prerequisites(long knowledgePointId, CurrentUser user) {
        KnowledgePoint point = requirePoint(knowledgePointId);
        long graphVersionId = requireActiveGraphVersion(point.getCourseId(), user);
        return toView(graphVersionId, publishedGraphStore.prerequisites(graphVersionId, knowledgePointId));
    }

    public GraphApi.GraphViewResponse successors(long knowledgePointId, CurrentUser user) {
        KnowledgePoint point = requirePoint(knowledgePointId);
        long graphVersionId = requireActiveGraphVersion(point.getCourseId(), user);
        return toView(graphVersionId, publishedGraphStore.successors(graphVersionId, knowledgePointId));
    }

    public GraphApi.GraphPathResponse path(long courseId, long fromKnowledgePointId, long toKnowledgePointId, CurrentUser user) {
        KnowledgePoint source = requirePoint(fromKnowledgePointId);
        KnowledgePoint target = requirePoint(toKnowledgePointId);
        if (!source.getCourseId().equals(courseId) || !target.getCourseId().equals(courseId)) {
            throw new ConflictException("path endpoints must belong to the requested course");
        }
        long graphVersionId = requireActiveGraphVersion(courseId, user);
        return new GraphApi.GraphPathResponse(
                graphVersionId, publishedGraphStore.path(graphVersionId, fromKnowledgePointId, toKnowledgePointId)
        );
    }

    public GraphApi.GraphViewResponse prerequisiteSubgraph(long knowledgePointId, CurrentUser user) {
        KnowledgePoint point = requirePoint(knowledgePointId);
        long graphVersionId = requireActiveGraphVersion(point.getCourseId(), user);
        return toView(graphVersionId, publishedGraphStore.prerequisiteSubgraph(graphVersionId, knowledgePointId));
    }

    private long requireActiveGraphVersion(long courseId, CurrentUser user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("course does not exist"));
        courseAccessService.requireCourseAccess(courseId, user);
        Long graphVersionId = course.getActiveGraphVersionId();
        if (graphVersionId == null) {
            throw new ConflictException("course does not have a published graph version");
        }
        GraphVersion graphVersion = graphVersionRepository.findById(graphVersionId)
                .orElseThrow(() -> new ConflictException("course active graph version does not exist"));
        if (graphVersion.getStatus() != GraphVersionStatus.PUBLISHED || !graphVersion.getCourseId().equals(courseId)) {
            throw new ConflictException("course active graph version is not a published graph for this course");
        }
        return graphVersionId;
    }

    private KnowledgePoint requirePoint(long knowledgePointId) {
        return knowledgePointRepository.findById(knowledgePointId)
                .orElseThrow(() -> new NotFoundException("knowledge point does not exist"));
    }

    private GraphApi.GraphViewResponse toView(long graphVersionId, PublishedGraphStore.GraphSlice slice) {
        List<GraphApi.GraphNodeResponse> nodes = slice.nodes().stream()
                .map(node -> new GraphApi.GraphNodeResponse(node.businessId(), node.knowledgeCode(), node.knowledgeName()))
                .toList();
        List<GraphApi.GraphEdgeResponse> edges = slice.edges().stream()
                .map(edge -> new GraphApi.GraphEdgeResponse(
                        edge.relationId(), edge.sourceKnowledgePointId(), edge.targetKnowledgePointId(),
                        "PREREQUISITE", edge.relationSource()
                ))
                .toList();
        return new GraphApi.GraphViewResponse(graphVersionId, nodes, edges);
    }
}
