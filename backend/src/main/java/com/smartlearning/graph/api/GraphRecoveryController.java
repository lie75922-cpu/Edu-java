package com.smartlearning.graph.api;

import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.graph.application.PublishedGraphReprojectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Operational recovery endpoint; it never creates or mutates a GraphVersion. */
@RestController
@RequestMapping("/api/v1/admin/graph-projections")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class GraphRecoveryController {

    private final PublishedGraphReprojectionService reprojectionService;

    public GraphRecoveryController(PublishedGraphReprojectionService reprojectionService) {
        this.reprojectionService = reprojectionService;
    }

    @PostMapping("/rebuild-published")
    public ApiResponse<List<PublishedGraphReprojectionService.ReprojectionResult>> rebuildPublishedGraphs() {
        return ApiResponse.ok(reprojectionService.rebuildActivePublishedGraphs());
    }
}
