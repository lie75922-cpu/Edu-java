package com.smartlearning.teacher.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.api.QuestionApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.api.TeacherAssignmentApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.course.application.TeacherAssignmentService;
import com.smartlearning.course.domain.TeacherAssignmentRole;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.application.GraphVersionService;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.learning.api.AnswerApi;
import com.smartlearning.learning.application.AnswerSubmissionService;
import com.smartlearning.mastery.application.MasteryUpdateWorker;
import com.smartlearning.recommendation.domain.RecommendationItem;
import com.smartlearning.recommendation.domain.RecommendationReasonCode;
import com.smartlearning.recommendation.domain.RecommendationSnapshot;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationItemRepository;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationSnapshotRepository;
import com.smartlearning.teacher.api.TeacherAnalyticsApi;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.teacher.analytics.integration.local",
        "spring.data.redis.repositories.enabled=false",
        "spring.data.neo4j.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000",
        "app.mastery-worker.initial-delay-ms=3600000",
        "app.mastery-worker.poll-interval-ms=3600000"
})
class TeacherAnalyticsMySqlIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_teacher_analytics_it")
            .withUsername("edu_teacher_analytics_it")
            .withPassword("edu_teacher_analytics_it_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private PlatformUserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CourseService courseService;
    @Autowired
    private TeacherAssignmentService teacherAssignmentService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private ExerciseUnitService exerciseUnitService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private AnswerSubmissionService answerSubmissionService;
    @Autowired
    private MasteryUpdateWorker masteryUpdateWorker;
    @Autowired
    private GraphVersionService graphVersionService;
    @Autowired
    private RecommendationSnapshotRepository recommendationSnapshotRepository;
    @Autowired
    private RecommendationItemRepository recommendationItemRepository;
    @Autowired
    private TeacherAnalyticsService teacherAnalyticsService;

    private CurrentUser systemAdmin;
    private CurrentUser teachingAdmin;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private CurrentUser studentA;
    private CurrentUser studentB;
    private long courseA;
    private long courseB;
    private long pointA1;
    private long pointA2;
    private long questionA1;
    private long questionA2;

    @BeforeEach
    void createPlatformBusinessFixture() {
        int number = SEQUENCE.incrementAndGet();
        systemAdmin = currentUser(createUser("system-admin-" + number, "System Admin", "SYSTEM_ADMIN"), "SYSTEM_ADMIN");
        teachingAdmin = currentUser(createUser("teach-admin-" + number, "Teaching Admin", "TEACH_ADMIN"), "TEACH_ADMIN");
        teacherA = currentUser(createUser("teacher-a-" + number, "Teacher A", "TEACHER"), "TEACHER");
        teacherB = currentUser(createUser("teacher-b-" + number, "Teacher B", "TEACHER"), "TEACHER");
        studentA = currentUser(createUser("student-a-" + number, "Student A", "STUDENT"), "STUDENT");
        studentB = currentUser(createUser("student-b-" + number, "Student B", "STUDENT"), "STUDENT");

        courseA = courseService.create(new CourseApi.CourseRequest(
                "TEACHER-A-" + number, "Teacher A Course", "Platform analytics fixture A", "ACTIVE"
        )).id();
        courseB = courseService.create(new CourseApi.CourseRequest(
                "TEACHER-B-" + number, "Teacher B Course", "Platform analytics fixture B", "ACTIVE"
        )).id();
        teacherAssignmentService.assign(courseA,
                new TeacherAssignmentApi.CreateAssignmentRequest(teacherA.id(), TeacherAssignmentRole.OWNER), systemAdmin);
        teacherAssignmentService.assign(courseB,
                new TeacherAssignmentApi.CreateAssignmentRequest(teacherB.id(), TeacherAssignmentRole.INSTRUCTOR), systemAdmin);
        courseService.enroll(courseA, studentA);
        courseService.enroll(courseB, studentB);

        pointA1 = createPoint(courseA, "A1-" + number, "Observed Point");
        pointA2 = createPoint(courseA, "A2-" + number, "Unknown Point");
        questionA1 = createQuestion(courseA, pointA1, "A-Q1-" + number, "First high-error platform question");
        questionA2 = createQuestion(courseA, pointA1, "A-Q2-" + number, "Second high-error platform question");
        long pointB = createPoint(courseB, "B1-" + number, "Teacher B Point");
        createQuestion(courseB, pointB, "B-Q1-" + number, "Teacher B question");

        for (int attempt = 0; attempt < 5; attempt++) {
            submitWrong(questionA1, studentA, "a-q1-" + number + "-" + attempt);
            submitWrong(questionA2, studentA, "a-q2-" + number + "-" + attempt);
        }
        createRecommendationContext();
    }

    @Test
    void aggregatesOnlyPlatformCourseFactsAndPreservesUnknownMastery() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now().plusSeconds(3600);

        TeacherAnalyticsApi.CourseOverviewResponse overview = teacherAnalyticsService.overview(courseA, from, to, teacherA);
        assertThat(overview.activeEnrolledStudents()).isEqualTo(1);
        assertThat(overview.studentsWithActivity()).isEqualTo(1);
        assertThat(overview.attemptCount()).isEqualTo(10);
        assertThat(overview.correctCount()).isZero();
        assertThat(overview.correctRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.observedMasteryStudents()).isEqualTo(1);
        assertThat(overview.masteryAlgorithmVersions()).contains("RULE_BETA_1_1_V1");

        TeacherAnalyticsApi.KnowledgePointAnalyticsPage knowledgePoints = teacherAnalyticsService.knowledgePoints(
                courseA, from, to, teacherA
        );
        TeacherAnalyticsApi.KnowledgePointAnalyticsResponse observed = knowledgePoints.items().stream()
                .filter(item -> item.knowledgePointId().equals(pointA1))
                .findFirst().orElseThrow();
        TeacherAnalyticsApi.KnowledgePointAnalyticsResponse unknown = knowledgePoints.items().stream()
                .filter(item -> item.knowledgePointId().equals(pointA2))
                .findFirst().orElseThrow();
        assertThat(observed.observedStudentCount()).isEqualTo(1);
        assertThat(observed.unknownStudentCount()).isZero();
        assertThat(observed.attemptCount()).isEqualTo(10);
        assertThat(unknown.observedStudentCount()).isZero();
        assertThat(unknown.unknownStudentCount()).isEqualTo(1);
        assertThat(unknown.meanMastery()).isNull();
        assertThat(unknown.correctRate()).isNull();

        TeacherAnalyticsApi.MasteryHeatmapResponse heatmap = teacherAnalyticsService.heatmap(courseA, 0, 1, teacherA);
        assertThat(heatmap.totalStudents()).isEqualTo(1);
        assertThat(heatmap.students()).singleElement().satisfies(student -> {
            assertThat(student.studentId()).isEqualTo(studentA.id());
            assertThat(student.items()).filteredOn(item -> item.knowledgePointId().equals(pointA1))
                    .singleElement().extracting(TeacherAnalyticsApi.HeatmapMasteryItem::status).isEqualTo("OBSERVED");
            assertThat(student.items()).filteredOn(item -> item.knowledgePointId().equals(pointA2))
                    .singleElement().satisfies(item -> {
                        assertThat(item.status()).isEqualTo("UNKNOWN");
                        assertThat(item.masteryScore()).isNull();
                    });
        });

        TeacherAnalyticsApi.HighErrorQuestionsResponse highError = teacherAnalyticsService.highErrorQuestions(
                courseA, from, to, 20, 5, teacherA
        );
        assertThat(highError.items()).extracting(TeacherAnalyticsApi.HighErrorQuestionResponse::questionId)
                .containsExactly(questionA1, questionA2);
        assertThat(highError.items()).allSatisfy(item -> {
            assertThat(item.attemptCount()).isEqualTo(5);
            assertThat(item.wrongCount()).isEqualTo(5);
            assertThat(item.wrongRate()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(item.associatedKnowledgePoints()).extracting(TeacherAnalyticsApi.AssociatedKnowledgePoint::knowledgePointId)
                    .containsExactly(pointA1);
        });

        TeacherAnalyticsApi.StudentDetailResponse detail = teacherAnalyticsService.studentDetail(courseA, studentA.id(), 20, 50, teacherA);
        assertThat(detail.studentId()).isEqualTo(studentA.id());
        assertThat(detail.mastery()).filteredOn(item -> item.knowledgePointId().equals(pointA2))
                .singleElement().extracting(TeacherAnalyticsApi.StudentMastery::status).isEqualTo("UNKNOWN");
        assertThat(detail.recentAnswers()).hasSize(10);
        assertThat(detail.masteryHistory()).hasSize(10);
        assertThat(detail.latestRecommendation()).isNotNull();
        assertThat(detail.latestRecommendation().masteryAlgorithmVersion()).isEqualTo("RULE_BETA_1_1_V1");
        assertThat(detail.latestRecommendation().items()).hasSize(1);
    }

    @Test
    void usesNullRatesForEmptyWindowsAndNeverLetsTeacherACrossCourseBoundaries() {
        TeacherAnalyticsApi.CourseOverviewResponse emptyWindow = teacherAnalyticsService.overview(
                courseA,
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-02T00:00:00Z"),
                teacherA
        );
        assertThat(emptyWindow.attemptCount()).isZero();
        assertThat(emptyWindow.correctRate()).isNull();

        assertThatThrownBy(() -> teacherAnalyticsService.overview(
                courseB, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), teacherA
        )).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> teacherAnalyticsService.studentDetail(courseA, studentB.id(), 10, 10, teacherA))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(teacherAnalyticsService.overview(
                courseB, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), teachingAdmin
        ).courseId()).isEqualTo(courseB);
    }

    @Test
    void recordsControlledFixtureLatencyWithoutClaimingProductionPerformance() {
        addPlatformPerformanceFixture(30, 10);
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now().plusSeconds(3600);

        for (int warmup = 0; warmup < 3; warmup++) {
            teacherAnalyticsService.overview(courseA, from, to, teacherA);
            teacherAnalyticsService.knowledgePoints(courseA, from, to, teacherA);
            teacherAnalyticsService.heatmap(courseA, 0, 25, teacherA);
            teacherAnalyticsService.highErrorQuestions(courseA, from, to, 20, 5, teacherA);
            teacherAnalyticsService.studentDetail(courseA, studentA.id(), 20, 50, teacherA);
        }

        List<Long> overviewNanos = new ArrayList<>();
        List<Long> knowledgePointNanos = new ArrayList<>();
        List<Long> heatmapNanos = new ArrayList<>();
        List<Long> highErrorNanos = new ArrayList<>();
        List<Long> studentDetailNanos = new ArrayList<>();
        for (int sample = 0; sample < 21; sample++) {
            overviewNanos.add(measure(() -> teacherAnalyticsService.overview(courseA, from, to, teacherA)));
            knowledgePointNanos.add(measure(() -> teacherAnalyticsService.knowledgePoints(courseA, from, to, teacherA)));
            heatmapNanos.add(measure(() -> teacherAnalyticsService.heatmap(courseA, 0, 25, teacherA)));
            highErrorNanos.add(measure(() -> teacherAnalyticsService.highErrorQuestions(courseA, from, to, 20, 5, teacherA)));
            studentDetailNanos.add(measure(() -> teacherAnalyticsService.studentDetail(courseA, studentA.id(), 20, 50, teacherA)));
        }

        long overviewP95 = percentileMillis(overviewNanos, 0.95);
        long knowledgePointP95 = percentileMillis(knowledgePointNanos, 0.95);
        long heatmapP95 = percentileMillis(heatmapNanos, 0.95);
        long highErrorP95 = percentileMillis(highErrorNanos, 0.95);
        long studentDetailP95 = percentileMillis(studentDetailNanos, 0.95);
        System.out.printf(
                "V06_CONTROLLED_PERFORMANCE fixture=31-active-platform-students,2-active-knowledge-points,310-answer-records,samples=21 "
                        + "overview_ms_p50=%d,overview_ms_p95=%d,kp_ms_p50=%d,kp_ms_p95=%d,heatmap_ms_p50=%d,heatmap_ms_p95=%d,"
                        + "high_error_ms_p50=%d,high_error_ms_p95=%d,student_detail_ms_p50=%d,student_detail_ms_p95=%d%n",
                percentileMillis(overviewNanos, 0.50), overviewP95,
                percentileMillis(knowledgePointNanos, 0.50), knowledgePointP95,
                percentileMillis(heatmapNanos, 0.50), heatmapP95,
                percentileMillis(highErrorNanos, 0.50), highErrorP95,
                percentileMillis(studentDetailNanos, 0.50), studentDetailP95
        );
        assertThat(overviewP95).isLessThan(500L);
        assertThat(knowledgePointP95).isLessThan(500L);
        assertThat(highErrorP95).isLessThan(500L);
        assertThat(heatmapP95).isLessThan(1000L);
        assertThat(studentDetailP95).isLessThan(1000L);
    }

    private PlatformUser createUser(String username, String nickname, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        PlatformUser user = new PlatformUser(username, "test-credential", nickname);
        user.addRole(role);
        return userRepository.save(user);
    }

    private CurrentUser currentUser(PlatformUser user, String role) {
        return new CurrentUser(user.getId(), user.getUsername(), Set.of(role));
    }

    private long createPoint(long courseId, String code, String name) {
        return knowledgeService.createPoint(new KnowledgeApi.KnowledgePointRequest(
                courseId, null, code, name, "PLATFORM", null, "MAPPED", "ACTIVE"
        )).id();
    }

    private long createQuestion(long courseId, long knowledgePointId, String code, String stem) {
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, code + "-EX", code + " Exercise", "PLATFORM", null,
                "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        exerciseUnitService.upsertMapping(exercise.id(), new ExerciseApi.MappingRequest(
                knowledgePointId, "MANUAL", BigDecimal.ONE, true
        ));
        return questionService.create(new QuestionApi.QuestionRequest(
                exercise.id(), "SINGLE_CHOICE", stem, List.of("A"), "A is correct", null, "ACTIVE",
                List.of(
                        new QuestionApi.OptionRequest("A", "Correct", 1),
                        new QuestionApi.OptionRequest("B", "Incorrect", 2)
                )
        ), systemAdmin.id()).id();
    }

    private void submitWrong(long questionId, CurrentUser student, String clientRequestId) {
        answerSubmissionService.submit(questionId, new AnswerApi.AnswerSubmissionRequest(
                List.of("B"), 10L, clientRequestId
        ), student);
        while (masteryUpdateWorker.processNext()) {
            // Consume each transactionally persisted platform answer before asserting its read model.
        }
    }

    private void addPlatformPerformanceFixture(int additionalStudents, int attemptsPerStudent) {
        int number = SEQUENCE.incrementAndGet();
        for (int studentIndex = 0; studentIndex < additionalStudents; studentIndex++) {
            CurrentUser student = currentUser(
                    createUser("performance-student-" + number + "-" + studentIndex, "Performance Student " + studentIndex, "STUDENT"),
                    "STUDENT"
            );
            courseService.enroll(courseA, student);
            for (int attempt = 0; attempt < attemptsPerStudent; attempt++) {
                answerSubmissionService.submit(questionA1, new AnswerApi.AnswerSubmissionRequest(
                        List.of("B"), 10L, "performance-" + number + "-" + studentIndex + "-" + attempt
                ), student);
            }
        }
    }

    private long measure(Runnable operation) {
        long startedAt = System.nanoTime();
        operation.run();
        return System.nanoTime() - startedAt;
    }

    private long percentileMillis(List<Long> samples, double percentile) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return Math.round(sorted.get(index) / 1_000_000.0d);
    }

    private void createRecommendationContext() {
        GraphApi.GraphVersionResponse graphVersion = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseA, "analytics recommendation context", false), systemAdmin.id()
        );
        RecommendationSnapshot snapshot = recommendationSnapshotRepository.save(new RecommendationSnapshot(
                studentA.id(), courseA, graphVersion.id(), "RULE_BETA_1_1_V1", "REC_RULE_V1"
        ));
        recommendationItemRepository.save(new RecommendationItem(
                snapshot.getId(), 1, pointA1, null, new BigDecimal("0.1429"), RecommendationReasonCode.LOW_MASTERY, "{}"
        ));
    }
}
