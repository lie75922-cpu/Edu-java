package com.smartlearning.recommendation.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.application.GraphProjectionWorker;
import com.smartlearning.graph.application.GraphValidationService;
import com.smartlearning.graph.application.GraphVersionService;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.learning.domain.AnswerRecord;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.mastery.application.MasteryQueryService;
import com.smartlearning.mastery.application.MasteryUpdateWorker;
import com.smartlearning.outbox.application.OutboxService;
import com.smartlearning.recommendation.api.RecommendationApi;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationItemRepository;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationSnapshotRepository;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.personalization.neo4j.integration.local",
        "spring.data.redis.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000",
        "app.mastery-worker.initial-delay-ms=3600000",
        "app.mastery-worker.poll-interval-ms=3600000"
})
class PersonalizationNeo4jIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String NEO4J_PASSWORD = "neo4j-personalization-test-password";
    private static final String FAILURE_CONSTRAINT = "test_v4_code_conflict";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_personalization_neo4j_it")
            .withUsername("edu_personalization_neo4j_it")
            .withPassword("edu_personalization_neo4j_it_password");

    @Container
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withAdminPassword(NEO4J_PASSWORD);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> NEO4J_PASSWORD);
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
    private CourseRepository courseRepository;
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
    private MasteryUpdateWorker masteryUpdateWorker;
    @Autowired
    private MasteryQueryService masteryQueryService;
    @Autowired
    private GraphVersionService graphVersionService;
    @Autowired
    private GraphValidationService graphValidationService;
    @Autowired
    private GraphProjectionWorker graphProjectionWorker;
    @Autowired
    private RecommendationService recommendationService;
    @Autowired
    private LearningPathService learningPathService;
    @Autowired
    private RecommendationSnapshotRepository snapshotRepository;
    @Autowired
    private RecommendationItemRepository itemRepository;
    @Autowired
    private Driver neo4jDriver;

    private CurrentUser administrator;
    private CurrentUser student;
    private long courseId;
    private long pointA;
    private long pointB;
    private long pointC;
    private long pointWithoutExercise;
    private long exerciseA;
    private long exerciseB;
    private long exerciseC;

    @BeforeEach
    void createFixture() {
        dropFailureConstraint();
        clearProjectedGraph();
        int number = SEQUENCE.incrementAndGet();
        Role adminRole = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
        Role studentRole = roleRepository.findByCode("STUDENT").orElseThrow();
        PlatformUser adminUser = new PlatformUser(
                "personalization-admin-" + number, passwordEncoder.encode("integration-password"), "Personalization Administrator"
        );
        adminUser.addRole(adminRole);
        adminUser = userRepository.save(adminUser);
        administrator = new CurrentUser(adminUser.getId(), adminUser.getUsername(), Set.of("SYSTEM_ADMIN"));

        PlatformUser studentUser = new PlatformUser(
                "personalization-student-" + number, passwordEncoder.encode("integration-password"), "Personalization Student"
        );
        studentUser.addRole(studentRole);
        studentUser = userRepository.save(studentUser);
        student = new CurrentUser(studentUser.getId(), studentUser.getUsername(), Set.of("STUDENT"));

        CourseApi.CourseResponse course = courseService.create(new CourseApi.CourseRequest(
                "PERSONALIZATION-IT-" + number,
                "Personalization Graph Course",
                "MySQL plus Neo4j Published Graph fixture",
                "ACTIVE"
        ));
        courseId = course.id();
        courseService.enroll(courseId, student);

        pointA = createPoint("A-" + number, "Point A");
        pointB = createPoint("B-" + number, "Point B");
        pointC = createPoint("C-" + number, "Point C");
        pointWithoutExercise = createPoint("NO-EX-" + number, "No exercise point");
        exerciseA = createMappedActiveExercise(pointA, number, "a");
        exerciseB = createMappedActiveExercise(pointB, number, "b");
        exerciseC = createMappedActiveExercise(pointC, number, "c");
    }

    @AfterEach
    void removeFailureConstraint() {
        dropFailureConstraint();
    }

    @Test
    void recommendationAndLearningPathUseTheRealPublishedGraphAndPersistAnAuditableSnapshot() {
        GraphApi.GraphVersionResponse version = publishGraph("chain", false, List.of(
                new Edge(pointA, pointB), new Edge(pointB, pointC)
        ));
        recordAnswer(exerciseA, true, "a-correct-1");
        recordAnswer(exerciseA, true, "a-correct-2");
        recordAnswer(exerciseB, false, "b-wrong");
        recordAnswer(exerciseC, false, "c-wrong");

        RecommendationApi.RecommendationSnapshotResponse recommendation = recommendationService.generate(courseId, student);
        RecommendationApi.LearningPathResponse path = learningPathService.learningPath(pointC, student);

        assertThat(recommendation.graphVersionId()).isEqualTo(version.id());
        assertThat(recommendation.masteryAlgorithmVersion()).isEqualTo("RULE_BETA_1_1_V1");
        assertThat(recommendation.recommendationRuleVersion()).isEqualTo("REC_RULE_V1");
        assertThat(recommendation.items()).isNotEmpty();
        assertThat(recommendation.items().getFirst().knowledgePointId()).isEqualTo(pointB);
        assertThat(recommendation.items().getFirst().reasonCode()).isEqualTo("UNMET_PREREQUISITE");
        assertThat(recommendation.items().getFirst().explanationJson())
                .contains("\"graphVersionId\":" + version.id(), "\"unmetPrerequisite\":true");
        assertThat(snapshotRepository.findById(recommendation.id())).isPresent();
        assertThat(itemRepository.findByRecommendationSnapshotIdOrderByRankNoAsc(recommendation.id())).hasSize(recommendation.items().size());

        assertThat(path.graphVersionId()).isEqualTo(version.id());
        assertThat(path.nodes()).extracting(RecommendationApi.LearningPathNodeResponse::knowledgePointId)
                .containsExactly(pointB, pointC);
        assertThat(path.nodes().getLast().reasonCode()).isEqualTo("TARGET_PRACTICE");
        assertThat(path.nodes()).allSatisfy(node -> assertThat(node.hasAvailableExercise()).isTrue());
    }

    @Test
    void handlesNoPublishedGraphBranchingDagNoPrerequisiteAndNoExercise() {
        assertThatThrownBy(() -> learningPathService.learningPath(pointC, student))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("published graph version");

        GraphApi.GraphVersionResponse version = publishGraph("branching", false, List.of(
                new Edge(pointA, pointC), new Edge(pointB, pointC)
        ));
        RecommendationApi.LearningPathResponse branching = learningPathService.learningPath(pointC, student);
        RecommendationApi.LearningPathResponse noPrerequisite = learningPathService.learningPath(pointWithoutExercise, student);

        assertThat(branching.graphVersionId()).isEqualTo(version.id());
        assertThat(branching.nodes()).extracting(RecommendationApi.LearningPathNodeResponse::knowledgePointId)
                .containsExactly(pointA, pointB, pointC);
        assertThat(noPrerequisite.nodes()).hasSize(1);
        assertThat(noPrerequisite.nodes().getFirst().knowledgePointId()).isEqualTo(pointWithoutExercise);
        assertThat(noPrerequisite.nodes().getFirst().hasAvailableExercise()).isFalse();
        assertThat(noPrerequisite.nodes().getFirst().reasonCode()).isEqualTo("TARGET_PRACTICE");
    }

    @Test
    void activeGraphVersionSwitchBindsNewRecommendationsAndPathsToTheNewProjection() {
        GraphApi.GraphVersionResponse v1 = publishGraph("v1 chain", false, List.of(
                new Edge(pointA, pointB), new Edge(pointB, pointC)
        ));
        recordAnswer(exerciseB, false, "switch-b-wrong");
        recordAnswer(exerciseC, false, "switch-c-wrong");
        assertThat(recommendationService.generate(courseId, student).graphVersionId()).isEqualTo(v1.id());

        GraphApi.GraphVersionResponse v2 = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "v2 copied graph", true), administrator.id()
        );
        graphVersionService.addManualRelation(
                v2.id(), new GraphApi.ManualRelationRequest(pointA, pointC, BigDecimal.ONE), administrator.id()
        );
        assertThat(graphValidationService.validate(v2.id()).hasErrors()).isFalse();
        graphVersionService.requestPublish(v2.id());
        processGraphVersion(v2.id());
        GraphApi.GraphVersionResponse publishedV2 = graphVersionService.get(v2.id());

        assertThat(publishedV2.status()).isEqualTo("PUBLISHED");
        assertThat(courseRepository.findById(courseId).orElseThrow().getActiveGraphVersionId()).isEqualTo(v2.id());
        assertThat(recommendationService.generate(courseId, student).graphVersionId()).isEqualTo(v2.id());
        assertThat(learningPathService.learningPath(pointC, student).graphVersionId()).isEqualTo(v2.id());
    }

    @Test
    void projectionFailureKeepsTheOldActiveGraphQueryableForRecommendationsAndPaths() {
        GraphApi.GraphVersionResponse v1 = publishGraph("stable v1", false, List.of(
                new Edge(pointA, pointB), new Edge(pointB, pointC)
        ));
        recordAnswer(exerciseB, false, "failure-b-wrong");
        recordAnswer(exerciseC, false, "failure-c-wrong");

        GraphApi.GraphVersionResponse v2 = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "failing v2", true), administrator.id()
        );
        assertThat(graphValidationService.validate(v2.id()).hasErrors()).isFalse();
        graphVersionService.requestPublish(v2.id());
        createFailureConstraint();
        processGraphVersion(v2.id());

        assertThat(graphVersionService.get(v2.id()).status()).isEqualTo(GraphVersionStatus.PROJECTION_FAILED.name());
        assertThat(graphVersionService.get(v1.id()).status()).isEqualTo(GraphVersionStatus.PUBLISHED.name());
        assertThat(courseRepository.findById(courseId).orElseThrow().getActiveGraphVersionId()).isEqualTo(v1.id());
        assertThat(recommendationService.generate(courseId, student).graphVersionId()).isEqualTo(v1.id());
        assertThat(learningPathService.learningPath(pointC, student).graphVersionId()).isEqualTo(v1.id());
    }

    @Test
    void measuresWarmLocalServiceLatencyAgainstRealMySqlAndNeo4j() {
        publishGraph("performance chain", false, List.of(
                new Edge(pointA, pointB), new Edge(pointB, pointC)
        ));
        recordAnswer(exerciseB, false, "performance-b-wrong");
        recordAnswer(exerciseC, false, "performance-c-wrong");

        // Warm connections and persistence mappings before sampling direct service latency.
        masteryQueryService.courseMastery(courseId, student);
        recommendationService.generate(courseId, student);
        learningPathService.learningPath(pointC, student);

        long masteryP95 = percentileMillis(20, () -> masteryQueryService.courseMastery(courseId, student));
        long recommendationP95 = percentileMillis(20, () -> recommendationService.generate(courseId, student));
        long learningPathP95 = percentileMillis(20, () -> learningPathService.learningPath(pointC, student));

        System.out.printf(
                "V04_PERF samples=20 masteryGetP95Ms=%d recommendationP95Ms=%d learningPathP95Ms=%d%n",
                masteryP95, recommendationP95, learningPathP95
        );
        assertThat(masteryP95).isGreaterThanOrEqualTo(0L);
        assertThat(recommendationP95).isGreaterThanOrEqualTo(0L);
        assertThat(learningPathP95).isGreaterThanOrEqualTo(0L);
    }

    private GraphApi.GraphVersionResponse publishGraph(String description, boolean copyActive, List<Edge> edges) {
        GraphApi.GraphVersionResponse version = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, description, copyActive), administrator.id()
        );
        for (Edge edge : edges) {
            graphVersionService.addManualRelation(
                    version.id(), new GraphApi.ManualRelationRequest(edge.sourceId(), edge.targetId(), BigDecimal.ONE), administrator.id()
            );
        }
        assertThat(graphValidationService.validate(version.id()).hasErrors()).isFalse();
        graphVersionService.requestPublish(version.id());
        processGraphVersion(version.id());
        return graphVersionService.get(version.id());
    }

    private long createPoint(String code, String name) {
        return knowledgeService.createPoint(new KnowledgeApi.KnowledgePointRequest(
                courseId, null, code, name, "PLATFORM", null, "MAPPED", "ACTIVE"
        )).id();
    }

    private long createMappedActiveExercise(long pointId, int number, String suffix) {
        long exerciseId = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, "PERSONALIZATION-EX-" + number + "-" + suffix, "Exercise " + suffix,
                "PLATFORM", null, "RESOLVED", "MAPPED", null, "ACTIVE"
        )).id();
        exerciseKnowledgeRepository.save(new ExerciseKnowledge(exerciseId, pointId, "TEST", BigDecimal.ONE, true));
        questionRepository.save(new Question(
                exerciseId, "SINGLE_CHOICE", "Active question " + suffix, "[\"A\"]", "test", null, "ACTIVE", administrator.id()
        ));
        return exerciseId;
    }

    private void recordAnswer(long exerciseId, boolean correct, String suffix) {
        Question question = questionRepository.findFirstByExerciseUnitIdAndStatusOrderByIdAsc(exerciseId, "ACTIVE").orElseThrow();
        AnswerRecord answer = answerRecordRepository.save(new AnswerRecord(
                student.id(), courseId, question.getId(), exerciseId, "[\"A\"]", correct, 1, 25L,
                "personalization-answer-" + suffix + "-" + SEQUENCE.incrementAndGet(), Instant.now()
        ));
        outboxService.enqueueMasteryUpdate(answer.getId(), student.id(), courseId, question.getId(), exerciseId, correct);
        assertThat(masteryUpdateWorker.processNext()).isTrue();
    }

    private void createFailureConstraint() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("""
                        CREATE CONSTRAINT test_v4_code_conflict IF NOT EXISTS
                        FOR (node:KnowledgePoint) REQUIRE node.code IS UNIQUE
                        """).consume();
                return null;
            });
        }
    }

    private void dropFailureConstraint() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("DROP CONSTRAINT test_v4_business_id_conflict IF EXISTS").consume();
                return null;
            });
        }
    }

    private void clearProjectedGraph() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("MATCH (node:KnowledgePoint) DETACH DELETE node").consume();
                return null;
            });
        }
    }

    private void processGraphVersion(long graphVersionId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String status = graphVersionService.get(graphVersionId).status();
            if (!"PUBLISHING".equals(status)) {
                return;
            }
            assertThat(graphProjectionWorker.processNext()).isTrue();
        }
        assertThat(graphVersionService.get(graphVersionId).status())
                .as("graph version should leave PUBLISHING after its outbox event is processed")
                .isNotEqualTo("PUBLISHING");
    }

    private long percentileMillis(int samples, Supplier<?> operation) {
        List<Long> durations = new ArrayList<>();
        for (int sample = 0; sample < samples; sample++) {
            long startedAt = System.nanoTime();
            operation.get();
            durations.add((System.nanoTime() - startedAt) / 1_000_000L);
        }
        Collections.sort(durations);
        return durations.get((int) Math.ceil(samples * 0.95d) - 1);
    }

    private record Edge(long sourceId, long targetId) {
    }
}
