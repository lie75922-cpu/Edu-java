package com.smartlearning.teacher.api;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.api.ApiResponse;
import com.smartlearning.teacher.application.TeacherAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'TEACH_ADMIN', 'TEACHER')")
public class TeacherAnalyticsController {

    private final TeacherAnalyticsService teacherAnalyticsService;

    public TeacherAnalyticsController(TeacherAnalyticsService teacherAnalyticsService) {
        this.teacherAnalyticsService = teacherAnalyticsService;
    }

    @GetMapping("/courses")
    public ApiResponse<List<TeacherAnalyticsApi.TeacherCourseResponse>> courses(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(teacherAnalyticsService.courses(CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/analytics/overview")
    public ApiResponse<TeacherAnalyticsApi.CourseOverviewResponse> overview(
            @PathVariable long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAnalyticsService.overview(courseId, from, to, CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/analytics/knowledge-points")
    public ApiResponse<TeacherAnalyticsApi.KnowledgePointAnalyticsPage> knowledgePoints(
            @PathVariable long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAnalyticsService.knowledgePoints(courseId, from, to, CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/analytics/mastery-heatmap")
    public ApiResponse<TeacherAnalyticsApi.MasteryHeatmapResponse> heatmap(
            @PathVariable long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAnalyticsService.heatmap(courseId, page, size, CurrentUser.from(jwt)));
    }

    @GetMapping("/courses/{courseId}/analytics/questions/errors")
    public ApiResponse<TeacherAnalyticsApi.HighErrorQuestionsResponse> highErrorQuestions(
            @PathVariable long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer minimumAttempts,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAnalyticsService.highErrorQuestions(
                courseId, from, to, limit, minimumAttempts, CurrentUser.from(jwt)
        ));
    }

    @GetMapping("/courses/{courseId}/students/{studentId}/analytics")
    public ApiResponse<TeacherAnalyticsApi.StudentDetailResponse> studentDetail(
            @PathVariable long courseId,
            @PathVariable long studentId,
            @RequestParam(required = false) Integer recentAnswersLimit,
            @RequestParam(required = false) Integer historyLimit,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.ok(teacherAnalyticsService.studentDetail(
                courseId, studentId, recentAnswersLimit, historyLimit, CurrentUser.from(jwt)
        ));
    }
}
