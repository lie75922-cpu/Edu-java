package com.smartlearning.teacher.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.BadRequestException;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.course.domain.Course;
import com.smartlearning.teacher.api.TeacherAnalyticsApi;
import com.smartlearning.teacher.infrastructure.persistence.TeacherAnalyticsQueryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeacherAnalyticsService {

    private static final BigDecimal WEAK_MASTERY_THRESHOLD = new BigDecimal("0.70");
    private static final int DEFAULT_HEATMAP_SIZE = 50;
    private static final int MAXIMUM_HEATMAP_SIZE = 100;
    private static final int DEFAULT_HIGH_ERROR_LIMIT = 20;
    private static final int MAXIMUM_HIGH_ERROR_LIMIT = 100;
    private static final int DEFAULT_MINIMUM_ATTEMPTS = 5;
    private static final int MAXIMUM_DETAIL_LIMIT = 100;
    private static final int DEFAULT_RECENT_ANSWER_LIMIT = 20;
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int STEM_PREVIEW_LIMIT = 160;

    private final CourseAccessService courseAccessService;
    private final CourseService courseService;
    private final TeacherAnalyticsWindowResolver windowResolver;
    private final TeacherAnalyticsQueryRepository queryRepository;

    public TeacherAnalyticsService(
            CourseAccessService courseAccessService,
            CourseService courseService,
            TeacherAnalyticsWindowResolver windowResolver,
            TeacherAnalyticsQueryRepository queryRepository
    ) {
        this.courseAccessService = courseAccessService;
        this.courseService = courseService;
        this.windowResolver = windowResolver;
        this.queryRepository = queryRepository;
    }

    public List<TeacherAnalyticsApi.TeacherCourseResponse> courses(CurrentUser user) {
        List<TeacherAnalyticsQueryRepository.CourseRow> rows;
        if (user.hasAnyRole("SYSTEM_ADMIN", "TEACH_ADMIN")) {
            rows = queryRepository.platformTeachingCourses();
        } else if (user.hasAnyRole("TEACHER")) {
            rows = queryRepository.assignedCourses(user.id());
        } else {
            throw new ForbiddenOperationException("teacher analytics access is required");
        }
        return rows.stream().map(row -> new TeacherAnalyticsApi.TeacherCourseResponse(
                row.courseId(), row.courseCode(), row.courseName(), row.status(), row.assignmentRole(),
                row.activeEnrollmentCount(), row.latestAnswerAt()
        )).toList();
    }

    public TeacherAnalyticsApi.CourseOverviewResponse overview(
            long courseId,
            Instant from,
            Instant to,
            CurrentUser user
    ) {
        courseAccessService.requireTeachingAccess(courseId, user);
        TeacherAnalyticsApi.ResolvedWindow window = windowResolver.resolve(from, to);
        TeacherAnalyticsQueryRepository.OverviewRow metrics = queryRepository.overview(courseId, window.from(), window.to());
        return new TeacherAnalyticsApi.CourseOverviewResponse(
                courseId,
                window,
                queryRepository.activeEnrollmentCount(courseId),
                metrics.studentsWithActivity(),
                metrics.attemptCount(),
                metrics.correctCount(),
                rate(metrics.correctCount(), metrics.attemptCount()),
                queryRepository.activeKnowledgePointCount(courseId),
                queryRepository.observedMasteryStudentCount(courseId),
                metrics.latestActivityAt(),
                queryRepository.masteryAlgorithmVersions(courseId)
        );
    }

    public TeacherAnalyticsApi.KnowledgePointAnalyticsPage knowledgePoints(
            long courseId,
            Instant from,
            Instant to,
            CurrentUser user
    ) {
        courseAccessService.requireTeachingAccess(courseId, user);
        TeacherAnalyticsApi.ResolvedWindow window = windowResolver.resolve(from, to);
        long activeEnrollmentCount = queryRepository.activeEnrollmentCount(courseId);
        List<TeacherAnalyticsApi.KnowledgePointAnalyticsResponse> items = queryRepository
                .knowledgePointAnalytics(courseId, window.from(), window.to())
                .stream()
                .map(row -> new TeacherAnalyticsApi.KnowledgePointAnalyticsResponse(
                        row.knowledgePointId(), row.knowledgeCode(), row.knowledgeName(), row.observedStudentCount(),
                        Math.max(0, activeEnrollmentCount - row.observedStudentCount()), row.meanMastery(),
                        row.weakObservedStudentCount(), row.attemptCount(), row.correctCount(),
                        rate(row.correctCount(), row.attemptCount()), splitVersions(row.algorithmVersions())
                ))
                .toList();
        return new TeacherAnalyticsApi.KnowledgePointAnalyticsPage(
                courseId,
                window,
                "CURRENT_STATE_ONLY; UNKNOWN has null masteryScore and is excluded from meanMastery",
                items
        );
    }

    public TeacherAnalyticsApi.MasteryHeatmapResponse heatmap(
            long courseId,
            int page,
            Integer requestedSize,
            CurrentUser user
    ) {
        courseAccessService.requireTeachingAccess(courseId, user);
        if (page < 0) {
            throw new BadRequestException("page must be zero or greater");
        }
        int size = requestedSize == null ? DEFAULT_HEATMAP_SIZE : requestedSize;
        if (size < 1 || size > MAXIMUM_HEATMAP_SIZE) {
            throw new BadRequestException("heatmap size must be between 1 and 100");
        }
        long offset;
        try {
            offset = Math.multiplyExact((long) page, size);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("page is too large");
        }
        List<TeacherAnalyticsQueryRepository.StudentRow> students = queryRepository.heatmapStudents(courseId, size, offset);
        List<TeacherAnalyticsQueryRepository.KnowledgePointCatalogRow> points = queryRepository.activeKnowledgePoints(courseId);
        Map<MasteryKey, TeacherAnalyticsQueryRepository.MasteryRow> mastery = queryRepository
                .masteryForHeatmapStudents(courseId, students.stream().map(TeacherAnalyticsQueryRepository.StudentRow::studentId).toList())
                .stream()
                .collect(Collectors.toMap(
                        row -> new MasteryKey(row.studentId(), row.knowledgePointId()),
                        row -> row
                ));
        List<TeacherAnalyticsApi.HeatmapStudent> responseStudents = students.stream().map(student ->
                new TeacherAnalyticsApi.HeatmapStudent(
                        student.studentId(),
                        student.displayName(),
                        points.stream().map(point -> heatmapItem(mastery.get(new MasteryKey(student.studentId(), point.knowledgePointId())), point))
                                .toList()
                )
        ).toList();
        return new TeacherAnalyticsApi.MasteryHeatmapResponse(
                courseId,
                page,
                size,
                queryRepository.activeHeatmapStudentCount(courseId),
                points.stream().map(point -> new TeacherAnalyticsApi.HeatmapKnowledgePoint(
                        point.knowledgePointId(), point.knowledgeCode(), point.knowledgeName()
                )).toList(),
                responseStudents
        );
    }

    public TeacherAnalyticsApi.HighErrorQuestionsResponse highErrorQuestions(
            long courseId,
            Instant from,
            Instant to,
            Integer requestedLimit,
            Integer requestedMinimumAttempts,
            CurrentUser user
    ) {
        courseAccessService.requireTeachingAccess(courseId, user);
        TeacherAnalyticsApi.ResolvedWindow window = windowResolver.resolve(from, to);
        int limit = requiredBoundedValue(requestedLimit, DEFAULT_HIGH_ERROR_LIMIT, MAXIMUM_HIGH_ERROR_LIMIT, "limit");
        int minimumAttempts = requiredBoundedValue(
                requestedMinimumAttempts, DEFAULT_MINIMUM_ATTEMPTS, Integer.MAX_VALUE, "minimumAttempts"
        );
        List<TeacherAnalyticsQueryRepository.HighErrorQuestionRow> rows = queryRepository.highErrorQuestions(
                courseId, window.from(), window.to(), minimumAttempts, limit
        );
        Map<Long, List<TeacherAnalyticsQueryRepository.AssociatedKnowledgePointRow>> pointsByQuestion = queryRepository
                .associatedKnowledgePoints(rows.stream().map(TeacherAnalyticsQueryRepository.HighErrorQuestionRow::questionId).toList());
        return new TeacherAnalyticsApi.HighErrorQuestionsResponse(
                courseId,
                window,
                minimumAttempts,
                rows.stream().map(row -> new TeacherAnalyticsApi.HighErrorQuestionResponse(
                        row.questionId(), row.exerciseUnitId(), row.exerciseCode(), row.exerciseName(), truncate(row.stem()),
                        row.attemptCount(), row.wrongCount(), rate(row.wrongCount(), row.attemptCount()), row.distinctStudents(),
                        pointsByQuestion.getOrDefault(row.questionId(), List.of()).stream()
                                .map(point -> new TeacherAnalyticsApi.AssociatedKnowledgePoint(
                                        point.knowledgePointId(), point.knowledgeCode(), point.knowledgeName()
                                )).toList()
                )).toList()
        );
    }

    public TeacherAnalyticsApi.StudentDetailResponse studentDetail(
            long courseId,
            long studentId,
            Integer requestedRecentAnswerLimit,
            Integer requestedHistoryLimit,
            CurrentUser user
    ) {
        courseAccessService.requireTeachingAccess(courseId, user);
        courseAccessService.requireActiveEnrollmentForTeaching(courseId, studentId);
        TeacherAnalyticsQueryRepository.StudentRow student = queryRepository.activeStudent(courseId, studentId);
        if (student == null) {
            throw new ForbiddenOperationException("the student is not actively enrolled in this course");
        }
        int recentAnswerLimit = requiredBoundedValue(
                requestedRecentAnswerLimit, DEFAULT_RECENT_ANSWER_LIMIT, MAXIMUM_DETAIL_LIMIT, "recentAnswersLimit"
        );
        int historyLimit = requiredBoundedValue(
                requestedHistoryLimit, DEFAULT_HISTORY_LIMIT, MAXIMUM_DETAIL_LIMIT, "historyLimit"
        );
        List<TeacherAnalyticsApi.StudentMastery> mastery = queryRepository.studentMastery(courseId, studentId).stream()
                .map(this::studentMastery)
                .toList();
        List<TeacherAnalyticsApi.StudentMastery> weakPoints = mastery.stream()
                .filter(item -> "OBSERVED".equals(item.status())
                        && item.masteryScore() != null
                        && item.masteryScore().compareTo(WEAK_MASTERY_THRESHOLD) < 0)
                .toList();
        TeacherAnalyticsQueryRepository.ActivityRow activity = queryRepository.activity(courseId, studentId);
        Course course = courseService.requireCourse(courseId);
        return new TeacherAnalyticsApi.StudentDetailResponse(
                courseId,
                studentId,
                student.displayName(),
                course.getActiveGraphVersionId(),
                new TeacherAnalyticsApi.StudentActivitySummary(
                        activity.attemptCount(), activity.correctCount(), rate(activity.correctCount(), activity.attemptCount()),
                        activity.latestActivityAt()
                ),
                mastery,
                weakPoints,
                queryRepository.recentAnswers(courseId, studentId, recentAnswerLimit).stream()
                        .map(row -> new TeacherAnalyticsApi.RecentAnswer(
                                row.answerRecordId(), row.questionId(), row.exerciseUnitId(), row.exerciseCode(),
                                row.exerciseName(), truncate(row.stem()), row.correct(), row.attemptNo(), row.durationMs(), row.answeredAt()
                        )).toList(),
                queryRepository.masteryHistory(courseId, studentId, historyLimit).stream()
                        .map(row -> new TeacherAnalyticsApi.MasteryHistory(
                                row.historyId(), row.knowledgePointId(), row.knowledgeCode(), row.knowledgeName(),
                                row.answerRecordId(), row.previousAttemptCount(), row.previousCorrectCount(), row.previousScore(),
                                row.newAttemptCount(), row.newCorrectCount(), row.newScore(), row.algorithmVersion(), row.createdAt()
                        )).toList(),
                latestRecommendation(courseId, studentId)
        );
    }

    private TeacherAnalyticsApi.HeatmapMasteryItem heatmapItem(
            TeacherAnalyticsQueryRepository.MasteryRow mastery,
            TeacherAnalyticsQueryRepository.KnowledgePointCatalogRow point
    ) {
        if (mastery == null) {
            return new TeacherAnalyticsApi.HeatmapMasteryItem(
                    point.knowledgePointId(), "UNKNOWN", null, 0, 0, null, null, null
            );
        }
        return new TeacherAnalyticsApi.HeatmapMasteryItem(
                point.knowledgePointId(), "OBSERVED", mastery.masteryScore(), mastery.attemptCount(), mastery.correctCount(),
                mastery.sourceType(), mastery.algorithmVersion(), mastery.lastAnsweredAt()
        );
    }

    private TeacherAnalyticsApi.StudentMastery studentMastery(TeacherAnalyticsQueryRepository.StudentMasteryRow row) {
        if (row.attemptCount() == null) {
            return new TeacherAnalyticsApi.StudentMastery(
                    row.knowledgePointId(), row.knowledgeCode(), row.knowledgeName(), "UNKNOWN", null, 0, 0,
                    null, null, null, null
            );
        }
        return new TeacherAnalyticsApi.StudentMastery(
                row.knowledgePointId(), row.knowledgeCode(), row.knowledgeName(), "OBSERVED", row.masteryScore(),
                row.attemptCount(), row.correctCount(), row.sourceType(), row.algorithmVersion(), row.lastAnsweredAt(), row.updatedAt()
        );
    }

    private TeacherAnalyticsApi.LatestRecommendationContext latestRecommendation(long courseId, long studentId) {
        TeacherAnalyticsQueryRepository.RecommendationSnapshotRow snapshot = queryRepository.latestRecommendation(courseId, studentId);
        if (snapshot == null) {
            return null;
        }
        return new TeacherAnalyticsApi.LatestRecommendationContext(
                snapshot.snapshotId(),
                snapshot.graphVersionId(),
                snapshot.masteryAlgorithmVersion(),
                snapshot.recommendationRuleVersion(),
                snapshot.generatedAt(),
                queryRepository.recommendationItems(snapshot.snapshotId()).stream()
                        .map(item -> new TeacherAnalyticsApi.RecommendationItemContext(
                                item.rank(), item.knowledgePointId(), item.knowledgeCode(), item.knowledgeName(),
                                item.exerciseUnitId(), item.exerciseCode(), item.exerciseName(), item.masteryScore(), item.reasonCode()
                        )).toList()
        );
    }

    private int requiredBoundedValue(Integer value, int defaultValue, int maximum, String name) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 1 || resolved > maximum) {
            throw new BadRequestException(name + " must be between 1 and " + maximum);
        }
        return resolved;
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private List<String> splitVersions(String versions) {
        if (versions == null || versions.isBlank()) {
            return List.of();
        }
        return List.of(versions.split("\\|"));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= STEM_PREVIEW_LIMIT) {
            return value;
        }
        return value.substring(0, STEM_PREVIEW_LIMIT) + "…";
    }

    private record MasteryKey(long studentId, long knowledgePointId) {
    }
}
