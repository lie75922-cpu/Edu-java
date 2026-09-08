package com.smartlearning.mastery.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.learning.domain.AnswerRecord;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.mastery.domain.StudentKnowledgeMastery;
import com.smartlearning.mastery.infrastructure.persistence.MasteryProcessedAnswerRepository;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryHistoryRepository;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryRepository;
import com.smartlearning.outbox.application.OutboxService;
import com.smartlearning.outbox.domain.OutboxEvent;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.mastery.integration.local",
        "spring.data.redis.repositories.enabled=false",
        "spring.data.neo4j.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000",
        "app.mastery-worker.initial-delay-ms=3600000",
        "app.mastery-worker.poll-interval-ms=3600000"
})
class MasteryWorkerMySqlIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_mastery_it")
            .withUsername("edu_mastery_it")
            .withPassword("edu_mastery_it_password");

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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CourseService courseService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private ExerciseUnitService exerciseUnitService;
    @Autowired
    private ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRecordRepository answerRecordRepository;
    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private MasteryUpdateWorker masteryUpdateWorker;
    @Autowired
    private StudentKnowledgeMasteryRepository masteryRepository;
    @Autowired
    private MasteryProcessedAnswerRepository processedAnswerRepository;
    @Autowired
    private StudentKnowledgeMasteryHistoryRepository historyRepository;

    private Fixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = createFixture("primary");
    }

    @Test
    void persistsFirstCorrectAndFirstWrongWithRuleHistoryInRealMySql() {
        long correctPoint = createPoint(fixture.courseId(), "CORRECT", "Correct point", "ACTIVE");
        long wrongPoint = createPoint(fixture.courseId(), "WRONG", "Wrong point", "ACTIVE");
        long correctExercise = createExercise(fixture.courseId(), "correct", "ACTIVE");
        long wrongExercise = createExercise(fixture.courseId(), "wrong", "ACTIVE");
        map(correctExercise, correctPoint);
        map(wrongExercise, wrongPoint);

        AnswerRecord correct = createAnswer(fixture, correctExercise, true, "first-correct");
        AnswerRecord wrong = createAnswer(fixture, wrongExercise, false, "first-wrong");
        outboxService.enqueueMasteryUpdate(correct.getId(), fixture.studentId(), fixture.courseId(), correct.getQuestionId(), correctExercise, true);
        outboxService.enqueueMasteryUpdate(wrong.getId(), fixture.studentId(), fixture.courseId(), wrong.getQuestionId(), wrongExercise, false);

        processAllMasteryEvents();

        assertMastery(fixture.studentId(), correctPoint, 1, 1, "0.6667");
        assertMastery(fixture.studentId(), wrongPoint, 1, 0, "0.3333");
        assertThat(historyRepository.findByStudentIdAndCourseIdOrderByCreatedAtDescIdDesc(fixture.studentId(), fixture.courseId()))
                .filteredOn(history -> history.getAnswerRecordId().equals(correct.getId())
                        || history.getAnswerRecordId().equals(wrong.getId()))
                .hasSize(2)
                .allSatisfy(history -> assertThat(history.getAlgorithmVersion()).isEqualTo("RULE_BETA_1_1_V1"));
    }

    @Test
    void oneAnswerMappedToTwoKnowledgePointsAndOneHundredReplayDeliveriesRemainExactlyOnce() {
        long firstPoint = createPoint(fixture.courseId(), "MULTI-A", "Multi point A", "ACTIVE");
        long secondPoint = createPoint(fixture.courseId(), "MULTI-B", "Multi point B", "ACTIVE");
        long exerciseId = createExercise(fixture.courseId(), "multi", "ACTIVE");
        map(exerciseId, firstPoint);
        map(exerciseId, secondPoint);
        AnswerRecord answer = createAnswer(fixture, exerciseId, true, "multi-answer");

        for (int attempt = 0; attempt < 100; attempt++) {
            outboxService.enqueueMasteryUpdate(
                    answer.getId(), fixture.studentId(), fixture.courseId(), answer.getQuestionId(), exerciseId, true
            );
        }
        processAllMasteryEvents();

        assertMastery(fixture.studentId(), firstPoint, 1, 1, "0.6667");
        assertMastery(fixture.studentId(), secondPoint, 1, 1, "0.6667");
        assertThat(processedAnswerRepository.findById(answer.getId())).isPresent();
        assertThat(historyRepository.findByStudentIdAndCourseIdOrderByCreatedAtDescIdDesc(fixture.studentId(), fixture.courseId()))
                .filteredOn(history -> history.getAnswerRecordId().equals(answer.getId()))
                .hasSize(2)
                .extracting(history -> history.getKnowledgePointId())
                .containsExactlyInAnyOrder(firstPoint, secondPoint);
        assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> MasteryUpdateEventProcessor.EVENT_TYPE.equals(event.getEventType())
                        && event.getAggregateId().equals(String.valueOf(answer.getId())))
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo("DONE"));
    }

    @Test
    void concurrentRetryDeliveriesClaimTheAnswerOnlyOnceAndIgnoreGraphEvents() throws Exception {
        long pointId = createPoint(fixture.courseId(), "CONCURRENT", "Concurrent point", "ACTIVE");
        long exerciseId = createExercise(fixture.courseId(), "concurrent", "ACTIVE");
        map(exerciseId, pointId);
        AnswerRecord answer = createAnswer(fixture, exerciseId, false, "concurrent-answer");
        for (int attempt = 0; attempt < 12; attempt++) {
            outboxService.enqueueMasteryUpdate(
                    answer.getId(), fixture.studentId(), fixture.courseId(), answer.getQuestionId(), exerciseId, false
            );
        }
        OutboxEvent graphEvent = outboxService.enqueueGraphRebuild(987654321L, fixture.courseId());

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Boolean>> deliveries = new ArrayList<>();
            for (int worker = 0; worker < 12; worker++) {
                deliveries.add(masteryUpdateWorker::processNext);
            }
            List<Future<Boolean>> results = executor.invokeAll(deliveries);
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
        processAllMasteryEvents();

        assertMastery(fixture.studentId(), pointId, 1, 0, "0.3333");
        assertThat(processedAnswerRepository.findById(answer.getId())).isPresent();
        assertThat(historyRepository.findByStudentIdAndCourseIdOrderByCreatedAtDescIdDesc(fixture.studentId(), fixture.courseId()))
                .filteredOn(history -> history.getAnswerRecordId().equals(answer.getId()))
                .hasSize(1);
        assertThat(outboxEventRepository.findById(graphEvent.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void unmappedInactiveAndCrossCourseMappingsFailWithoutFabricatingMastery() {
        long unmappedExercise = createExercise(fixture.courseId(), "unmapped", "ACTIVE");
        AnswerRecord unmapped = createAnswer(fixture, unmappedExercise, false, "unmapped-answer");
        OutboxEvent unmappedEvent = outboxService.enqueueMasteryUpdate(
                unmapped.getId(), fixture.studentId(), fixture.courseId(), unmapped.getQuestionId(), unmappedExercise, false
        );

        long inactivePoint = createPoint(fixture.courseId(), "INACTIVE", "Inactive point", "DISABLED");
        long inactiveExercise = createExercise(fixture.courseId(), "inactive", "ACTIVE");
        map(inactiveExercise, inactivePoint);
        AnswerRecord inactive = createAnswer(fixture, inactiveExercise, false, "inactive-answer");
        OutboxEvent inactiveEvent = outboxService.enqueueMasteryUpdate(
                inactive.getId(), fixture.studentId(), fixture.courseId(), inactive.getQuestionId(), inactiveExercise, false
        );

        Fixture otherCourse = createFixture("other-course");
        long otherCoursePoint = createPoint(otherCourse.courseId(), "OTHER", "Other course point", "ACTIVE");
        long crossCourseExercise = createExercise(fixture.courseId(), "cross-course", "ACTIVE");
        exerciseKnowledgeRepository.save(new ExerciseKnowledge(
                crossCourseExercise, otherCoursePoint, "TEST", BigDecimal.ONE, true
        ));
        AnswerRecord crossCourse = createAnswer(fixture, crossCourseExercise, false, "cross-course-answer");
        OutboxEvent crossCourseEvent = outboxService.enqueueMasteryUpdate(
                crossCourse.getId(), fixture.studentId(), fixture.courseId(), crossCourse.getQuestionId(), crossCourseExercise, false
        );

        processAllMasteryEvents();

        assertFailedWithoutMastery(unmappedEvent, "no KnowledgePoint mapping");
        assertFailedWithoutMastery(inactiveEvent, "inactive KnowledgePoint");
        assertFailedWithoutMastery(crossCourseEvent, "crosses courses");
        assertThat(processedAnswerRepository.findById(unmapped.getId())).isEmpty();
        assertThat(processedAnswerRepository.findById(inactive.getId())).isEmpty();
        assertThat(processedAnswerRepository.findById(crossCourse.getId())).isEmpty();
    }

    private Fixture createFixture(String suffix) {
        int number = SEQUENCE.incrementAndGet();
        Role studentRole = roleRepository.findByCode("STUDENT").orElseThrow();
        PlatformUser student = new PlatformUser(
                "mastery-it-" + suffix + "-" + number,
                passwordEncoder.encode("integration-password"),
                "Mastery Integration Student"
        );
        student.addRole(studentRole);
        student = userRepository.save(student);
        CourseApi.CourseResponse course = courseService.create(new CourseApi.CourseRequest(
                "MASTERY-IT-" + suffix + "-" + number,
                "Mastery Integration Course",
                "MySQL 8.4 Rule Mastery fixture",
                "ACTIVE"
        ));
        return new Fixture(student.getId(), course.id(), number);
    }

    private long createPoint(long courseId, String code, String name, String status) {
        return knowledgeService.createPoint(new KnowledgeApi.KnowledgePointRequest(
                courseId, null, code + "-" + SEQUENCE.incrementAndGet(), name, "PLATFORM", null, "MAPPED", status
        )).id();
    }

    private long createExercise(long courseId, String suffix, String status) {
        return exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, "MASTERY-EX-" + suffix + "-" + SEQUENCE.incrementAndGet(), "Exercise " + suffix,
                "PLATFORM", null, "RESOLVED", "UNMAPPED", null, status
        )).id();
    }

    private void map(long exerciseId, long pointId) {
        exerciseKnowledgeRepository.save(new ExerciseKnowledge(exerciseId, pointId, "TEST", BigDecimal.ONE, true));
    }

    private AnswerRecord createAnswer(Fixture owner, long exerciseId, boolean correct, String requestSuffix) {
        Question question = questionRepository.save(new Question(
                exerciseId, "SINGLE_CHOICE", "Mastery test question", "[\"A\"]", "test", null, "ACTIVE", owner.studentId()
        ));
        return answerRecordRepository.save(new AnswerRecord(
                owner.studentId(), owner.courseId(), question.getId(), exerciseId, "[\"A\"]", correct, 1,
                50L, "mastery-request-" + requestSuffix + "-" + SEQUENCE.incrementAndGet(), Instant.now()
        ));
    }

    private void processAllMasteryEvents() {
        while (masteryUpdateWorker.processNext()) {
            // Drain only MASTERY_UPDATE_REQUEST; graph events remain untouched by this worker.
        }
    }

    private void assertMastery(long studentId, long knowledgePointId, int attempts, int correct, String expectedScore) {
        StudentKnowledgeMastery mastery = masteryRepository.findByStudentIdAndKnowledgePointId(studentId, knowledgePointId).orElseThrow();
        assertThat(mastery.getAttemptCount()).isEqualTo(attempts);
        assertThat(mastery.getCorrectCount()).isEqualTo(correct);
        assertThat(mastery.getMasteryScore()).isEqualByComparingTo(expectedScore);
        assertThat(mastery.getSourceType()).isEqualTo("RULE");
        assertThat(mastery.getAlgorithmVersion()).isEqualTo("RULE_BETA_1_1_V1");
    }

    private void assertFailedWithoutMastery(OutboxEvent event, String expectedReasonFragment) {
        OutboxEvent persisted = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("FAILED");
        assertThat(persisted.getLastError()).contains(expectedReasonFragment);
    }

    private record Fixture(long studentId, long courseId, int number) {
    }
}
