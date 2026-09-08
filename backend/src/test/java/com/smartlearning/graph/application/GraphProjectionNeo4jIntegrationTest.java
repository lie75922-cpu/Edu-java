package com.smartlearning.graph.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.graph.infrastructure.neo4j.Neo4jPublishedGraphStore;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.neo4j.integration.local",
        "spring.data.redis.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000"
})
class GraphProjectionNeo4jIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String NEO4J_PASSWORD = "neo4j-test-password";
    private static final String FAILURE_CONSTRAINT = "test_v2_business_id_conflict";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_graph_neo4j_it")
            .withUsername("edu_graph_neo4j_it")
            .withPassword("edu_graph_neo4j_it_password");

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
    private CourseService courseService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private ExerciseUnitService exerciseUnitService;
    @Autowired
    private GraphVersionService graphVersionService;
    @Autowired
    private GraphValidationService graphValidationService;
    @Autowired
    private GraphProjectionWorker graphProjectionWorker;
    @Autowired
    private GraphQueryService graphQueryService;
    @Autowired
    private PublishedGraphStore publishedGraphStore;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private Driver neo4jDriver;
    @Autowired
    private PlatformUserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private CurrentUser administrator;
    private long courseId;
    private long pointA;
    private long pointB;
    private long pointC;

    @BeforeEach
    void createFixture() {
        dropFailureConstraint();
        int number = SEQUENCE.incrementAndGet();
        Role administratorRole = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
        PlatformUser administratorUser = new PlatformUser(
                "neo4j-admin-" + number, passwordEncoder.encode("integration-password"), "Neo4j Administrator"
        );
        administratorUser.addRole(administratorRole);
        administratorUser = userRepository.save(administratorUser);
        administrator = new CurrentUser(administratorUser.getId(), administratorUser.getUsername(), Set.of("SYSTEM_ADMIN"));

        CourseApi.CourseResponse course = courseService.create(new CourseApi.CourseRequest(
                "NEO4J-IT-" + number, "Neo4j Graph Course", "V0.3 real Neo4j fixture", "ACTIVE"
        ));
        courseId = course.id();
        pointA = createPoint("A-" + number, "Point A");
        pointB = createPoint("B-" + number, "Point B");
        pointC = createPoint("C-" + number, "Point C");
        createMappedExercise(pointA, number, "a");
        createMappedExercise(pointB, number, "b");
        createMappedExercise(pointC, number, "c");
    }

    @AfterEach
    void removeFailureConstraint() {
        dropFailureConstraint();
    }

    @Test
    void projectsDagIntoRealNeo4jAndSupportsVersionIsolatedQueries() {
        GraphApi.GraphVersionResponse v1 = publishDag(false, "first graph", false);

        assertThat(v1.status()).isEqualTo("PUBLISHED");
        assertThat(graphQueryService.graph(courseId, administrator).graphVersionId()).isEqualTo(v1.id());
        assertThat(graphQueryService.graph(courseId, administrator).nodes()).hasSize(3);
        assertThat(graphQueryService.graph(courseId, administrator).edges()).hasSize(2);
        assertThat(graphQueryService.prerequisites(pointC, administrator).nodes())
                .extracting(GraphApi.GraphNodeResponse::id).containsExactlyInAnyOrder(pointB, pointC);
        assertThat(graphQueryService.successors(pointA, administrator).nodes())
                .extracting(GraphApi.GraphNodeResponse::id).containsExactlyInAnyOrder(pointA, pointB);
        assertThat(graphQueryService.path(courseId, pointA, pointC, administrator).knowledgePointIds())
                .containsExactly(pointA, pointB, pointC);
        assertThat(graphQueryService.prerequisiteSubgraph(pointC, administrator).nodes())
                .extracting(GraphApi.GraphNodeResponse::id).containsExactlyInAnyOrder(pointA, pointB, pointC);
        assertThat(constraintCount()).isEqualTo(1L);

        GraphApi.GraphVersionResponse v2 = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "copied graph", true), administrator.id()
        );
        graphVersionService.addManualRelation(
                v2.id(), new GraphApi.ManualRelationRequest(pointA, pointC, BigDecimal.ONE), administrator.id()
        );
        assertThat(graphValidationService.validate(v2.id()).hasErrors()).isFalse();
        graphVersionService.requestPublish(v2.id());
        assertThat(graphProjectionWorker.processNext()).isTrue();

        assertThat(graphVersionService.get(v1.id()).status()).isEqualTo("ARCHIVED");
        assertThat(graphVersionService.get(v2.id()).status()).isEqualTo("PUBLISHED");
        assertThat(graphQueryService.graph(courseId, administrator).graphVersionId()).isEqualTo(v2.id());
        assertThat(graphQueryService.graph(courseId, administrator).edges()).hasSize(3);
        assertThat(publishedGraphStore.graph(v1.id()).edges()).hasSize(2);
        assertThat(publishedGraphStore.graph(v2.id()).edges()).hasSize(3);
    }

    @Test
    void projectionFailureLeavesPreviousActiveVersionQueryableInRealNeo4j() {
        GraphApi.GraphVersionResponse v1 = publishDag(false, "stable graph", false);
        assertThat(graphQueryService.graph(courseId, administrator).graphVersionId()).isEqualTo(v1.id());

        GraphApi.GraphVersionResponse v2 = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "failing graph", true), administrator.id()
        );
        assertThat(graphValidationService.validate(v2.id()).hasErrors()).isFalse();
        graphVersionService.requestPublish(v2.id());
        createFailureConstraint();

        assertThat(graphProjectionWorker.processNext()).isTrue();

        assertThat(graphVersionService.get(v2.id()).status()).isEqualTo("PROJECTION_FAILED");
        assertThat(graphVersionService.get(v1.id()).status()).isEqualTo("PUBLISHED");
        assertThat(courseRepository.findById(courseId).orElseThrow().getActiveGraphVersionId()).isEqualTo(v1.id());
        assertThat(graphQueryService.graph(courseId, administrator).graphVersionId()).isEqualTo(v1.id());
        assertThat(graphQueryService.graph(courseId, administrator).edges()).hasSize(2);
    }

    private void createFailureConstraint() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("""
                        CREATE CONSTRAINT test_v2_business_id_conflict IF NOT EXISTS
                        FOR (node:KnowledgePoint) REQUIRE node.businessId IS UNIQUE
                        """).consume();
                return null;
            });
        }
    }

    private void dropFailureConstraint() {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(transaction -> {
                transaction.run("DROP CONSTRAINT test_v2_business_id_conflict IF EXISTS").consume();
                return null;
            });
        }
    }

    private GraphApi.GraphVersionResponse publishDag(boolean copyActive, String description, boolean includeShortcut) {
        GraphApi.GraphVersionResponse version = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, description, copyActive), administrator.id()
        );
        graphVersionService.addManualRelation(
                version.id(), new GraphApi.ManualRelationRequest(pointA, pointB, BigDecimal.ONE), administrator.id()
        );
        graphVersionService.addManualRelation(
                version.id(), new GraphApi.ManualRelationRequest(pointB, pointC, BigDecimal.ONE), administrator.id()
        );
        if (includeShortcut) {
            graphVersionService.addManualRelation(
                    version.id(), new GraphApi.ManualRelationRequest(pointA, pointC, BigDecimal.ONE), administrator.id()
            );
        }
        assertThat(graphValidationService.validate(version.id()).hasErrors()).isFalse();
        graphVersionService.requestPublish(version.id());
        assertThat(graphProjectionWorker.processNext()).isTrue();
        return graphVersionService.get(version.id());
    }

    private long createPoint(String code, String name) {
        return knowledgeService.createPoint(new KnowledgeApi.KnowledgePointRequest(
                courseId, null, code, name, "PLATFORM", null, "MAPPED", "ACTIVE"
        )).id();
    }

    private void createMappedExercise(long pointId, int number, String suffix) {
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, "NEO-EX-" + number + "-" + suffix, "Exercise " + suffix, "PLATFORM", null,
                "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        exerciseUnitService.upsertMapping(exercise.id(), new ExerciseApi.MappingRequest(
                pointId, "PLATFORM", BigDecimal.ONE, true
        ));
    }

    private long constraintCount() {
        try (Session session = neo4jDriver.session()) {
            return session.executeRead(transaction -> transaction.run("""
                    SHOW CONSTRAINTS YIELD name
                    WHERE name = $name
                    RETURN count(*) AS total
                    """, java.util.Map.of("name", "knowledge_point_projection_key_unique"))
                    .single().get("total").asLong());
        }
    }

}
