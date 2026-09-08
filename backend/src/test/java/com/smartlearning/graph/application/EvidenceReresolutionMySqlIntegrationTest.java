package com.smartlearning.graph.application;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.domain.EvidenceReresolutionTrigger;
import com.smartlearning.graph.domain.EvidenceResolutionStatus;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceLinkRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationEvidenceRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.evidence-reresolution.local",
        "spring.data.redis.repositories.enabled=false",
        "spring.data.neo4j.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000"
})
class EvidenceReresolutionMySqlIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_evidence_reresolution_it")
            .withUsername("edu_evidence_reresolution_it")
            .withPassword("edu_evidence_reresolution_it_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private CourseService courseService;
    @Autowired private KnowledgeService knowledgeService;
    @Autowired private ExerciseUnitService exerciseUnitService;
    @Autowired private GraphVersionService graphVersionService;
    @Autowired private EvidenceImportService evidenceImportService;
    @Autowired private EvidenceReresolutionService evidenceReresolutionService;
    @Autowired private KnowledgeRelationEvidenceRepository evidenceRepository;
    @Autowired private KnowledgeRelationRepository relationRepository;
    @Autowired private KnowledgeRelationEvidenceLinkRepository evidenceLinkRepository;
    @Autowired private PlatformUserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long administratorId;
    private long courseId;
    private long pointA;
    private long pointB;
    private long pointC;

    @BeforeEach
    void createFixture() {
        int number = SEQUENCE.incrementAndGet();
        Role role = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
        PlatformUser administrator = new PlatformUser(
                "evidence-admin-" + number, passwordEncoder.encode("integration-password"), "Evidence Administrator"
        );
        administrator.addRole(role);
        administratorId = userRepository.save(administrator).getId();
        courseId = courseService.create(new CourseApi.CourseRequest(
                "EVIDENCE-V005-" + number, "Evidence re-resolution", "V005 MySQL fixture", "ACTIVE"
        )).id();
        pointA = createPoint("A-" + number, "Point A");
        pointB = createPoint("B-" + number, "Point B");
        pointC = createPoint("C-" + number, "Point C");
    }

    @Test
    void unmappedEvidenceBecomesResolvedWithoutChangingRawEvidence() {
        GraphApi.GraphVersionResponse draft = draft("unmapped to resolved");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-unmapped", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-unmapped");
        KnowledgeRelationEvidence evidence = importEvidence(draft.id(), "unmapped", source.externalId(), target.externalId());
        String rawPayload = evidence.getRawPayloadJson();

        assertThat(evidence.getResolutionStatus()).isEqualTo(EvidenceResolutionStatus.UNMAPPED_TARGET);
        exerciseUnitService.upsertMapping(target.id(), mapping(pointB));

        GraphApi.EvidenceReresolutionResult result = apply(draft.id(), evidence.getId());

        KnowledgeRelationEvidence after = evidenceRepository.findById(evidence.getId()).orElseThrow();
        assertThat(result.changedEvidenceCount()).isEqualTo(1);
        assertThat(after.getResolutionStatus()).isEqualTo(EvidenceResolutionStatus.RESOLVED);
        assertThat(after.getSourceKnowledgePointId()).isEqualTo(pointA);
        assertThat(after.getTargetKnowledgePointId()).isEqualTo(pointB);
        assertThat(after.getExternalEvidenceId()).isEqualTo(evidence.getExternalEvidenceId());
        assertThat(after.getSourceExternalId()).isEqualTo(source.externalId());
        assertThat(after.getTargetExternalId()).isEqualTo(target.externalId());
        assertThat(after.getRawPayloadJson()).isEqualTo(rawPayload);
        KnowledgeRelation relation = relation(draft.id(), pointA, pointB);
        assertThat(relation.getEvidenceCount()).isEqualTo(1);
        assertThat(evidenceLinkRepository.findByRelationIdOrderByIdAsc(relation.getId())).extracting(link -> link.getEvidenceId())
                .containsExactly(evidence.getId());
        assertThat(evidenceReresolutionService.history(evidence.getId())).singleElement()
                .satisfies(history -> {
                    assertThat(history.oldResolutionStatus()).isEqualTo("UNMAPPED_TARGET");
                    assertThat(history.newResolutionStatus()).isEqualTo("RESOLVED");
                    assertThat(history.operatorId()).isEqualTo(administratorId);
                    assertThat(history.triggerType()).isEqualTo("MAPPING_CHANGE");
                    assertThat(history.graphVersionId()).isEqualTo(draft.id());
                });
    }

    @Test
    void ambiguousEvidenceBecomesResolvedAfterMappingIsMadeUnique() {
        GraphApi.GraphVersionResponse draft = draft("ambiguous to resolved");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-ambiguous", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-ambiguous", pointB, pointC);
        KnowledgeRelationEvidence evidence = importEvidence(draft.id(), "ambiguous", source.externalId(), target.externalId());

        assertThat(evidence.getResolutionStatus()).isEqualTo(EvidenceResolutionStatus.AMBIGUOUS_TARGET_MAPPING);
        exerciseUnitService.removeMapping(target.id(), pointC);

        apply(draft.id(), evidence.getId());

        assertThat(evidenceRepository.findById(evidence.getId()).orElseThrow().getResolutionStatus())
                .isEqualTo(EvidenceResolutionStatus.RESOLVED);
        assertThat(relation(draft.id(), pointA, pointB).getEvidenceCount()).isEqualTo(1);
    }

    @Test
    void resolvedPairMoveReconcilesStaleLinksAndAccurateEvidenceCounts() {
        GraphApi.GraphVersionResponse draft = draft("pair move");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-pair", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-pair", pointB);
        KnowledgeRelationEvidence moving = importEvidence(draft.id(), "move-1", source.externalId(), target.externalId());
        KnowledgeRelationEvidence retained = importEvidence(draft.id(), "move-2", source.externalId(), target.externalId());
        KnowledgeRelation oldRelation = relation(draft.id(), pointA, pointB);
        assertThat(oldRelation.getEvidenceCount()).isEqualTo(2);

        exerciseUnitService.removeMapping(target.id(), pointB);
        exerciseUnitService.upsertMapping(target.id(), mapping(pointC));
        apply(draft.id(), moving.getId());

        KnowledgeRelation afterOld = relation(draft.id(), pointA, pointB);
        KnowledgeRelation afterNew = relation(draft.id(), pointA, pointC);
        assertThat(afterOld.getEvidenceCount()).isEqualTo(1);
        assertThat(afterOld.getReviewStatus()).isEqualTo(RelationReviewStatus.CANDIDATE);
        assertThat(evidenceLinkRepository.findByRelationIdOrderByIdAsc(afterOld.getId())).extracting(link -> link.getEvidenceId())
                .containsExactly(retained.getId());
        assertThat(afterNew.getEvidenceCount()).isEqualTo(1);
        assertThat(evidenceLinkRepository.findByRelationIdOrderByIdAsc(afterNew.getId())).extracting(link -> link.getEvidenceId())
                .containsExactly(moving.getId());
        assertThat(evidenceRepository.findById(moving.getId()).orElseThrow().getTargetKnowledgePointId()).isEqualTo(pointC);
    }

    @Test
    void resolvedEvidenceCanBecomeAmbiguousAndDetachesItsOnlyCandidateLink() {
        GraphApi.GraphVersionResponse draft = draft("resolved to ambiguous");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-degrade", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-degrade", pointB);
        KnowledgeRelationEvidence evidence = importEvidence(draft.id(), "degrade", source.externalId(), target.externalId());
        KnowledgeRelation oldRelation = relation(draft.id(), pointA, pointB);

        exerciseUnitService.upsertMapping(target.id(), mapping(pointC));
        apply(draft.id(), evidence.getId());

        assertThat(evidenceRepository.findById(evidence.getId()).orElseThrow().getResolutionStatus())
                .isEqualTo(EvidenceResolutionStatus.AMBIGUOUS_TARGET_MAPPING);
        KnowledgeRelation afterOld = relation(draft.id(), pointA, pointB);
        assertThat(afterOld.getEvidenceCount()).isEqualTo(0);
        assertThat(afterOld.getReviewStatus()).isEqualTo(RelationReviewStatus.REJECTED);
        assertThat(evidenceLinkRepository.findByRelationIdOrderByIdAsc(afterOld.getId())).isEmpty();
    }

    @Test
    void selfLoopTransitionsBothDirectionsAndNeverLeavesUnsupportedEvidenceCandidate() {
        GraphApi.GraphVersionResponse draft = draft("self loop transitions");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-loop", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-loop", pointA);
        KnowledgeRelationEvidence evidence = importEvidence(draft.id(), "loop", source.externalId(), target.externalId());

        assertThat(evidence.getResolutionStatus()).isEqualTo(EvidenceResolutionStatus.REJECTED_SELF_LOOP);
        exerciseUnitService.removeMapping(target.id(), pointA);
        exerciseUnitService.upsertMapping(target.id(), mapping(pointB));
        apply(draft.id(), evidence.getId());
        KnowledgeRelation relation = relation(draft.id(), pointA, pointB);
        assertThat(relation.getEvidenceCount()).isEqualTo(1);

        exerciseUnitService.removeMapping(target.id(), pointB);
        exerciseUnitService.upsertMapping(target.id(), mapping(pointA));
        apply(draft.id(), evidence.getId());

        assertThat(evidenceRepository.findById(evidence.getId()).orElseThrow().getResolutionStatus())
                .isEqualTo(EvidenceResolutionStatus.REJECTED_SELF_LOOP);
        KnowledgeRelation afterSelfLoop = relation(draft.id(), pointA, pointB);
        assertThat(afterSelfLoop.getEvidenceCount()).isEqualTo(0);
        assertThat(afterSelfLoop.getReviewStatus()).isEqualTo(RelationReviewStatus.REJECTED);
        assertThat(evidenceReresolutionService.history(evidence.getId())).hasSize(2);
    }

    @Test
    void dryRunHasZeroPersistenceSideEffects() {
        GraphApi.GraphVersionResponse draft = draft("dry run");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-dry-run", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-dry-run");
        KnowledgeRelationEvidence evidence = importEvidence(draft.id(), "dry-run", source.externalId(), target.externalId());
        long evidenceRows = count("knowledge_relation_evidence");
        long relationRows = count("knowledge_relation");
        long linkRows = count("knowledge_relation_evidence_link");
        long historyRows = count("knowledge_relation_evidence_resolution_history");
        long importRunRows = count("knowledge_relation_evidence_import_run");
        long conflictRows = count("knowledge_relation_evidence_conflict");
        String rawPayload = evidence.getRawPayloadJson();
        exerciseUnitService.upsertMapping(target.id(), mapping(pointB));

        GraphApi.EvidenceReresolutionResult result = evidenceReresolutionService.dryRun(
                draft.id(), request(evidence.getId()), administratorId
        );

        assertThat(result.mode()).isEqualTo("DRY_RUN");
        assertThat(result.changedEvidenceCount()).isEqualTo(1);
        assertThat(result.evidence()).singleElement().satisfies(item -> {
            assertThat(item.currentResolution().resolutionStatus()).isEqualTo("UNMAPPED_TARGET");
            assertThat(item.proposedResolution().resolutionStatus()).isEqualTo("RESOLVED");
            assertThat(item.affectedDraftRelations()).extracting(GraphApi.DraftRelationImpactResponse::action)
                    .containsExactly("CREATE_CANDIDATE_AND_ATTACH_EVIDENCE");
        });
        assertThat(count("knowledge_relation_evidence")).isEqualTo(evidenceRows);
        assertThat(count("knowledge_relation")).isEqualTo(relationRows);
        assertThat(count("knowledge_relation_evidence_link")).isEqualTo(linkRows);
        assertThat(count("knowledge_relation_evidence_resolution_history")).isEqualTo(historyRows);
        assertThat(count("knowledge_relation_evidence_import_run")).isEqualTo(importRunRows);
        assertThat(count("knowledge_relation_evidence_conflict")).isEqualTo(conflictRows);
        KnowledgeRelationEvidence after = evidenceRepository.findById(evidence.getId()).orElseThrow();
        assertThat(after.getResolutionStatus()).isEqualTo(EvidenceResolutionStatus.UNMAPPED_TARGET);
        assertThat(after.getRawPayloadJson()).isEqualTo(rawPayload);
    }

    @Test
    void repeatedApplyIsIdempotentAndCreatesNoSecondHistoryOrLink() {
        GraphApi.GraphVersionResponse draft = draft("idempotent apply");
        ExerciseApi.ExerciseUnitResponse source = createExercise("source-repeat", pointA);
        ExerciseApi.ExerciseUnitResponse target = createExercise("target-repeat");
        KnowledgeRelationEvidence evidence = importEvidence(draft.id(), "repeat", source.externalId(), target.externalId());
        exerciseUnitService.upsertMapping(target.id(), mapping(pointB));

        GraphApi.EvidenceReresolutionResult first = apply(draft.id(), evidence.getId());
        GraphApi.EvidenceReresolutionResult second = apply(draft.id(), evidence.getId());

        KnowledgeRelation relation = relation(draft.id(), pointA, pointB);
        assertThat(first.changedEvidenceCount()).isEqualTo(1);
        assertThat(second.changedEvidenceCount()).isZero();
        assertThat(relation.getEvidenceCount()).isEqualTo(1);
        assertThat(evidenceLinkRepository.findByRelationIdOrderByIdAsc(relation.getId())).hasSize(1);
        assertThat(evidenceReresolutionService.history(evidence.getId())).hasSize(1);
    }

    private GraphApi.GraphVersionResponse draft(String description) {
        return graphVersionService.create(new GraphApi.CreateGraphVersionRequest(courseId, description, false), administratorId);
    }

    private long createPoint(String code, String name) {
        return knowledgeService.createPoint(new KnowledgeApi.KnowledgePointRequest(
                courseId, null, code, name, "PLATFORM", null, "MAPPED", "ACTIVE"
        )).id();
    }

    private ExerciseApi.ExerciseUnitResponse createExercise(String externalId, long... mappings) {
        int number = SEQUENCE.get();
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, "EVIDENCE-EX-" + number + "-" + externalId, externalId,
                "JUNYI_CATALOG", externalId, "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        for (long mapping : mappings) {
            exerciseUnitService.upsertMapping(exercise.id(), mapping(mapping));
        }
        return exercise;
    }

    private ExerciseApi.MappingRequest mapping(long knowledgePointId) {
        return new ExerciseApi.MappingRequest(knowledgePointId, "V005_TEST", BigDecimal.ONE, true);
    }

    private KnowledgeRelationEvidence importEvidence(long graphVersionId, String suffix, String sourceExternalId, String targetExternalId) {
        String externalEvidenceId = "v005-evidence-" + SEQUENCE.get() + "-" + suffix;
        evidenceImportService.apply(graphVersionId, new GraphApi.EvidenceImportRequest(List.of(
                new GraphApi.RawPrerequisiteEvidenceRequest(
                        externalEvidenceId, sourceExternalId, targetExternalId, Map.of("fixture", externalEvidenceId)
                )
        )), administratorId);
        return evidenceRepository.findByCourseIdAndSourceTypeAndExternalEvidenceId(
                courseId, EvidenceResolutionResolver.JUNYI_RAW_PREREQUISITE, externalEvidenceId
        ).orElseThrow();
    }

    private GraphApi.EvidenceReresolutionResult apply(long graphVersionId, long evidenceId) {
        return evidenceReresolutionService.apply(graphVersionId, request(evidenceId), administratorId);
    }

    private GraphApi.EvidenceReresolutionRequest request(long evidenceId) {
        return new GraphApi.EvidenceReresolutionRequest(List.of(evidenceId), EvidenceReresolutionTrigger.MAPPING_CHANGE);
    }

    private KnowledgeRelation relation(long graphVersionId, long sourceKnowledgePointId, long targetKnowledgePointId) {
        return relationRepository.findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
                graphVersionId, sourceKnowledgePointId, targetKnowledgePointId, "PREREQUISITE"
        ).orElseThrow();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
