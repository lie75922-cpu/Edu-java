package com.smartlearning.graph.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.GraphVersionStatus;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceLinkRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.graph.integration.local",
        "spring.data.redis.repositories.enabled=false",
        "spring.data.neo4j.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000"
})
class GraphGovernanceMySqlIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_graph_it")
            .withUsername("edu_graph_it")
            .withPassword("edu_graph_it_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
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
    private EvidenceImportService evidenceImportService;
    @Autowired
    private KnowledgeRelationRepository relationRepository;
    @Autowired
    private KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private PlatformUserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long administratorId;
    private long courseId;
    private long pointA;
    private long pointB;
    private long pointC;

    @BeforeEach
    void createFixture() {
        int number = SEQUENCE.incrementAndGet();
        Role administratorRole = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
        PlatformUser administrator = new PlatformUser(
                "graph-admin-" + number, passwordEncoder.encode("integration-password"), "Graph Administrator"
        );
        administrator.addRole(administratorRole);
        administrator = userRepository.save(administrator);
        administratorId = administrator.getId();

        CourseApi.CourseResponse course = courseService.create(new CourseApi.CourseRequest(
                "GRAPH-IT-" + number, "Graph Integration Course", "V0.3 MySQL fixture", "ACTIVE"
        ));
        courseId = course.id();
        pointA = createPoint("A-" + number, "Point A");
        pointB = createPoint("B-" + number, "Point B");
        pointC = createPoint("C-" + number, "Point C");
        createMappedExercise("exercise-a-" + number, pointA, number);
        createMappedExercise("exercise-b-" + number, pointB, number);
        createMappedExercise("exercise-c-" + number, pointC, number);
    }

    @Test
    void flywayV003CreatesGovernanceTablesAndPublishRequestWritesOnlyGraphOutboxEvent() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('knowledge_relation_evidence', 'graph_version', 'knowledge_relation',
                                     'knowledge_relation_evidence_link', 'graph_validation_issue')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(5);

        GraphApi.GraphVersionResponse version = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "first published fixture", false), administratorId
        );
        graphVersionService.addManualRelation(
                version.id(), new GraphApi.ManualRelationRequest(pointA, pointB, BigDecimal.ONE), administratorId
        );
        GraphValidationService.ValidationOutcome validation = graphValidationService.validate(version.id());
        assertThat(validation.hasErrors()).isFalse();
        assertThat(validation.graphVersion().getStatus()).isEqualTo(GraphVersionStatus.READY);

        graphVersionService.requestPublish(version.id());

        assertThat(graphVersionService.get(version.id()).status()).isEqualTo("PUBLISHING");
        assertThat(outboxEventRepository.findAll()).anyMatch(event ->
                "GRAPH_REBUILD_REQUEST".equals(event.getEventType())
                        && String.valueOf(version.id()).equals(event.getAggregateId())
        );
        assertThat(outboxEventRepository.findAll()).noneMatch(event ->
                "MASTERY_UPDATE_REQUEST".equals(event.getEventType())
        );
    }

    @Test
    void validationFailureBlocksPublishAndDoesNotWriteOutbox() {
        GraphApi.GraphVersionResponse version = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "self loop fixture", false), administratorId
        );
        graphVersionService.addManualRelation(
                version.id(), new GraphApi.ManualRelationRequest(pointA, pointA, null), administratorId
        );

        GraphValidationService.ValidationOutcome validation = graphValidationService.validate(version.id());

        assertThat(validation.hasErrors()).isTrue();
        assertThat(graphVersionService.get(version.id()).status()).isEqualTo("VALIDATION_FAILED");
        long before = outboxEventRepository.count();
        assertThatThrownBy(() -> graphVersionService.requestPublish(version.id()))
                .hasMessageContaining("ready graph version");
        assertThat(outboxEventRepository.count()).isEqualTo(before);
    }

    @Test
    void rawEvidenceRetainsConflictsAndAggregatesAllEvidenceLinksForCandidatePair() {
        int number = SEQUENCE.get();
        GraphApi.GraphVersionResponse version = graphVersionService.create(
                new GraphApi.CreateGraphVersionRequest(courseId, "evidence fixture", false), administratorId
        );
        String source = "exercise-a-" + number;
        String target = "exercise-b-" + number;
        createDuplicateIdentityExercise("duplicate-source-" + number, pointA, number);
        createDuplicateIdentityExercise("duplicate-source-" + number, pointC, number + 10_000);

        GraphApi.EvidenceImportResult result = evidenceImportService.apply(version.id(), new GraphApi.EvidenceImportRequest(List.of(
                evidence("raw-1-" + number, source, target),
                evidence("raw-2-" + number, source, target),
                evidence("raw-self-" + number, source, source),
                evidence("raw-missing-" + number, "absent-" + number, target),
                evidence("raw-ambiguous-" + number, "duplicate-source-" + number, target)
        )), administratorId);

        assertThat(result.createdEvidence()).isEqualTo(5);
        assertThat(result.conflictCount()).isEqualTo(3);
        assertThat(result.conflicts()).extracting(GraphApi.EvidenceConflictResponse::conflictCode)
                .contains("REJECTED_SELF_LOOP", "MISSING_SOURCE_EXERCISE", "AMBIGUOUS_SOURCE_EXERCISE");
        assertThat(relationRepository.findByGraphVersionIdOrderByIdAsc(version.id())).isEmpty();

        GraphApi.CandidateRelationImportResult candidateResult = evidenceImportService.applyCandidates(
                version.id(), new GraphApi.CandidateRelationImportRequest(List.of(
                        new GraphApi.CandidateTopicRelationRequest(
                                "candidate-" + number, "A-" + number, "B-" + number,
                                "GRAPH_IT_POLICY_V1", "REVIEW_REQUIRED_NOT_PUBLISHED", "NOT_PUBLISHED",
                                List.of("raw-1-" + number, "raw-2-" + number)
                        )
                )), administratorId
        );

        assertThat(candidateResult.createdCandidateRelations()).isEqualTo(1);
        assertThat(candidateResult.conflictCount()).isZero();
        assertThat(relationRepository.findByGraphVersionIdOrderByIdAsc(version.id())).hasSize(1);
        var relation = relationRepository.findByGraphVersionIdOrderByIdAsc(version.id()).getFirst();
        assertThat(relation.getEvidenceCount()).isEqualTo(2);
        assertThat(relation.getReviewStatus().name()).isEqualTo("CANDIDATE");
        assertThat(evidenceLinkRepository.findByRelationIdOrderByIdAsc(relation.getId())).hasSize(2);
        assertThat(evidenceImportService.listEvidence(courseId))
                .filteredOn(evidence -> evidence.externalEvidenceId().equals("raw-self-" + number))
                .singleElement()
                .extracting(GraphApi.EvidenceResponse::resolutionStatus)
                .isEqualTo("REJECTED_SELF_LOOP");
    }

    private long createPoint(String code, String name) {
        return knowledgeService.createPoint(new KnowledgeApi.KnowledgePointRequest(
                courseId, null, code, name, "JUNYI_TOPIC", code, "MAPPED", "ACTIVE"
        )).id();
    }

    private void createMappedExercise(String externalId, long pointId, int number) {
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, "EX-" + number + "-" + externalId, externalId, "JUNYI_CATALOG", externalId,
                "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        exerciseUnitService.upsertMapping(exercise.id(), new ExerciseApi.MappingRequest(
                pointId, "JUNYI_TOPIC", BigDecimal.ONE, false
        ));
    }

    private void createDuplicateIdentityExercise(String externalId, long pointId, int suffix) {
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, "DUP-" + suffix, "Duplicate " + suffix, "JUNYI_CATALOG", externalId,
                "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        exerciseUnitService.upsertMapping(exercise.id(), new ExerciseApi.MappingRequest(
                pointId, "JUNYI_TOPIC", BigDecimal.ONE, false
        ));
    }

    private GraphApi.RawPrerequisiteEvidenceRequest evidence(String id, String source, String target) {
        return new GraphApi.RawPrerequisiteEvidenceRequest(id, source, target, Map.of("fixture", id));
    }
}
