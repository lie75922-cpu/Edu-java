package com.smartlearning.graph.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.graph.application.GraphQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GraphQueryController {

    private final GraphQueryService graphQueryService;

    public GraphQueryController(GraphQueryService graphQueryService) {
        this.graphQueryService = graphQueryService;
    }

    @GetMapping("/courses/{courseId}/graph")
    public ApiResponse<GraphApi.GraphViewResponse> graph(
            @PathVariable long courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphQueryService.graph(courseId, CurrentUser.from(jwt)));
    }

    @GetMapping("/knowledge-points/{knowledgePointId}/prerequisites")
    public ApiResponse<GraphApi.GraphViewResponse> prerequisites(
            @PathVariable long knowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphQueryService.prerequisites(knowledgePointId, CurrentUser.from(jwt)));
    }

    @GetMapping("/knowledge-points/{knowledgePointId}/successors")
    public ApiResponse<GraphApi.GraphViewResponse> successors(
            @PathVariable long knowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphQueryService.successors(knowledgePointId, CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/graph/path")
    public ApiResponse<GraphApi.GraphPathResponse> path(
            @PathVariable long courseId,
            @RequestParam long from,
            @RequestParam long to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphQueryService.path(courseId, from, to, CurrentUser.from(jwt)));
    }

    @GetMapping("/knowledge-points/{knowledgePointId}/prerequisite-subgraph")
    public ApiResponse<GraphApi.GraphViewResponse> prerequisiteSubgraph(
            @PathVariable long knowledgePointId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(graphQueryService.prerequisiteSubgraph(knowledgePointId, CurrentUser.from(jwt)));
    }
}
