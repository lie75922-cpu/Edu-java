package com.smartlearning.teacher.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TeacherAnalyticsApi {

    private TeacherAnalyticsApi() {
    }

    public record ResolvedWindow(Instant from, Instant to) {
    }

    public record TeacherCourseResponse(
            Long courseId,
            String courseCode,
            String courseName,
            String status,
            String assignmentRole,
            long activeEnrollmentCount,
            Instant latestAnswerAt
    ) {
    }

    public record CourseOverviewResponse(
            Long courseId,
            ResolvedWindow window,
            long activeEnrolledStudents,
            long studentsWithActivity,
            long attemptCount,
            long correctCount,
            BigDecimal correctRate,
            long activeKnowledgePointCount,
            long observedMasteryStudents,
            Instant latestActivityAt,
            List<String> masteryAlgorithmVersions
    ) {
    }

    public record KnowledgePointAnalyticsResponse(
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            long observedStudentCount,
            long unknownStudentCount,
            BigDecimal meanMastery,
            long weakObservedStudentCount,
            long attemptCount,
            long correctCount,
            BigDecimal correctRate,
            List<String> masteryAlgorithmVersions
    ) {
    }

    public record KnowledgePointAnalyticsPage(
            Long courseId,
            ResolvedWindow answerWindow,
            String masteryStateSemantics,
            List<KnowledgePointAnalyticsResponse> items
    ) {
    }

    public record HeatmapKnowledgePoint(
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName
    ) {
    }

    public record HeatmapMasteryItem(
            Long knowledgePointId,
            String status,
            BigDecimal masteryScore,
            int attemptCount,
            int correctCount,
            String sourceType,
            String algorithmVersion,
            Instant lastAnsweredAt
    ) {
    }

    public record HeatmapStudent(
            Long studentId,
            String displayName,
            List<HeatmapMasteryItem> items
    ) {
    }

    public record MasteryHeatmapResponse(
            Long courseId,
            int page,
            int size,
            long totalStudents,
            List<HeatmapKnowledgePoint> knowledgePoints,
            List<HeatmapStudent> students
    ) {
    }

    public record AssociatedKnowledgePoint(Long knowledgePointId, String knowledgeCode, String knowledgeName) {
    }

    public record HighErrorQuestionResponse(
            Long questionId,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            String stemPreview,
            long attemptCount,
            long wrongCount,
            BigDecimal wrongRate,
            long distinctStudents,
            List<AssociatedKnowledgePoint> associatedKnowledgePoints
    ) {
    }

    public record HighErrorQuestionsResponse(
            Long courseId,
            ResolvedWindow window,
            int minimumAttempts,
            List<HighErrorQuestionResponse> items
    ) {
    }

    public record StudentMastery(
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            String status,
            BigDecimal masteryScore,
            int attemptCount,
            int correctCount,
            String sourceType,
            String algorithmVersion,
            Instant lastAnsweredAt,
            Instant updatedAt
    ) {
    }

    public record RecentAnswer(
            Long answerRecordId,
            Long questionId,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            String stemPreview,
            boolean correct,
            int attemptNo,
            Long durationMs,
            Instant answeredAt
    ) {
    }

    public record MasteryHistory(
            Long historyId,
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            Long answerRecordId,
            int previousAttemptCount,
            int previousCorrectCount,
            BigDecimal previousScore,
            int newAttemptCount,
            int newCorrectCount,
            BigDecimal newScore,
            String algorithmVersion,
            Instant createdAt
    ) {
    }

    public record RecommendationItemContext(
            int rank,
            Long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            BigDecimal masteryScore,
            String reasonCode
    ) {
    }

    public record LatestRecommendationContext(
            Long snapshotId,
            Long graphVersionId,
            String masteryAlgorithmVersion,
            String recommendationRuleVersion,
            Instant generatedAt,
            List<RecommendationItemContext> items
    ) {
    }

    public record StudentActivitySummary(
            long attemptCount,
            long correctCount,
            BigDecimal correctRate,
            Instant latestActivityAt
    ) {
    }

    public record StudentDetailResponse(
            Long courseId,
            Long studentId,
            String displayName,
            Long currentPublishedGraphVersionId,
            StudentActivitySummary activity,
            List<StudentMastery> mastery,
            List<StudentMastery> weakObservedPoints,
            List<RecentAnswer> recentAnswers,
            List<MasteryHistory> masteryHistory,
            LatestRecommendationContext latestRecommendation
    ) {
    }
}
