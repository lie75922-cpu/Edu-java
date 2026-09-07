package com.smartlearning.learning.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.api.QuestionApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.learning.api.AnswerApi;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.integration.local",
        "app.security.jwt.access-token-ttl-seconds=3600",
        "spring.data.redis.repositories.enabled=false",
        "spring.data.neo4j.repositories.enabled=false"
})
class AnswerOutboxMySqlIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_it")
            .withUsername("edu_it")
            .withPassword("edu_it_password");

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
    private ExerciseUnitService exerciseUnitService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private AnswerSubmissionService answerSubmissionService;
    @Autowired
    private AnswerRecordRepository answerRecordRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private CurrentUser student;
    private long questionId;

    @BeforeEach
    void createPlatformOnlyFixture() {
        int number = SEQUENCE.incrementAndGet();
        Role studentRole = roleRepository.findByCode("STUDENT").orElseThrow();
        PlatformUser user = new PlatformUser("it-learner-" + number, passwordEncoder.encode("integration-password"), "Integration Learner");
        user.addRole(studentRole);
        user = userRepository.save(user);
        student = new CurrentUser(user.getId(), user.getUsername(), Set.of("STUDENT"));

        CourseApi.CourseResponse course = courseService.create(new CourseApi.CourseRequest(
                "IT-COURSE-" + number, "Integration Course", "MySQL transactional integration fixture", "ACTIVE"
        ));
        courseService.enroll(course.id(), student);
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                course.id(), "IT-EX-" + number, "Integration Exercise", "PLATFORM", null,
                "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        QuestionApi.AdminQuestionResponse question = questionService.create(new QuestionApi.QuestionRequest(
                exercise.id(), "SINGLE_CHOICE", "Which option is correct?", List.of("A"), "A is correct.", null, "ACTIVE",
                List.of(
                        new QuestionApi.OptionRequest("A", "Correct", 1),
                        new QuestionApi.OptionRequest("B", "Incorrect", 2)
                )
        ), user.getId());
        questionId = question.id();
    }

    @Test
    void flywayMySqlSchemaPersistsAnswerAndTransactionalOutboxTogether() {
        long outboxBefore = outboxEventRepository.count();

        AnswerApi.AnswerResultResponse result = answerSubmissionService.submit(
                questionId, new AnswerApi.AnswerSubmissionRequest(List.of("A"), 25L, "it-request-" + questionId), student
        );

        assertThat(result.correct()).isTrue();
        assertThat(answerRecordRepository.findByStudentIdAndClientRequestId(student.id(), "it-request-" + questionId)).isPresent();
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBefore + 1);
        assertThat(outboxEventRepository.findAll())
                .anyMatch(event -> "MASTERY_UPDATE_REQUEST".equals(event.getEventType())
                        && String.valueOf(result.answerRecordId()).equals(event.getAggregateId()));
    }

    @Test
    void databaseWriteFailureRollsBackTheAnswerTransactionWithoutOutboxResidue() {
        long answersBefore = answerRecordRepository.count();
        long outboxBefore = outboxEventRepository.count();
        String tooLongClientRequestId = "x".repeat(129);

        assertThatThrownBy(() -> answerSubmissionService.submit(
                questionId, new AnswerApi.AnswerSubmissionRequest(List.of("A"), 25L, tooLongClientRequestId), student
        )).isInstanceOf(RuntimeException.class);

        assertThat(answerRecordRepository.count()).isEqualTo(answersBefore);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxBefore);
    }
}
