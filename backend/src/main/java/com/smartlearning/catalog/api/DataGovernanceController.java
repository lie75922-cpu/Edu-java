package com.smartlearning.catalog.api;

import com.smartlearning.catalog.application.DataGovernanceService;
import com.smartlearning.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/data-governance")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class DataGovernanceController {

    private final DataGovernanceService dataGovernanceService;

    public DataGovernanceController(DataGovernanceService dataGovernanceService) {
        this.dataGovernanceService = dataGovernanceService;
    }

    @GetMapping("/overview")
    public ApiResponse<DataGovernanceApi.OverviewResponse> overview() {
        return ApiResponse.ok(dataGovernanceService.overview());
    }

    @GetMapping("/import-runs")
    public ApiResponse<DataGovernanceApi.ImportRunsResponse> importRuns() {
        return ApiResponse.ok(dataGovernanceService.importRuns());
    }
}
