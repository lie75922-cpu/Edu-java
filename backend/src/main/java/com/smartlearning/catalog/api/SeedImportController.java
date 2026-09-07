package com.smartlearning.catalog.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.catalog.application.SeedImportService;
import com.smartlearning.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/seed-imports")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SeedImportController {

    private final SeedImportService seedImportService;

    public SeedImportController(SeedImportService seedImportService) {
        this.seedImportService = seedImportService;
    }

    @PostMapping("/dry-run")
    public ApiResponse<SeedImportApi.SeedImportResult> dryRun(
            @Valid @RequestBody SeedImportApi.SeedImportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(seedImportService.dryRun(request, CurrentUser.from(jwt).id()));
    }

    @PostMapping("/apply")
    public ApiResponse<SeedImportApi.SeedImportResult> apply(
            @Valid @RequestBody SeedImportApi.SeedImportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(seedImportService.apply(request, CurrentUser.from(jwt).id()));
    }

    @GetMapping("/{importRunId}/conflicts")
    public ApiResponse<List<SeedImportApi.SeedConflictResponse>> conflicts(@PathVariable long importRunId) {
        return ApiResponse.ok(seedImportService.conflicts(importRunId));
    }
}
