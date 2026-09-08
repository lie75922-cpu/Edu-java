package com.smartlearning.teacher.api;

import com.smartlearning.assessment.api.ExerciseApi;
import com.smartlearning.assessment.api.QuestionApi;
import com.smartlearning.assessment.application.ExerciseUnitService;
import com.smartlearning.assessment.application.QuestionService;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.api.TeacherAssignmentApi;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.course.application.TeacherAssignmentService;
import com.smartlearning.course.domain.TeacherAssignmentRole;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import com.smartlearning.graph.api.GraphApi;
import com.smartlearning.graph.application.EvidenceImportService;
import com.smartlearning.graph.application.GraphVersionService;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.application.KnowledgeService;
import com.smartlearning.teacher.application.TeacherAnalyticsService;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.jwt.issuer=https://edu-java.teacher.authorization.integration.local",
        "spring.data.redis.repositories.enabled=false",
        "spring.data.neo4j.repositories.enabled=false",
        "app.graph-projection.initial-delay-ms=3600000",
        "app.graph-projection.poll-interval-ms=3600000",
        "app.mastery-worker.initial-delay-ms=3600000",
        "app.mastery-worker.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
class TeacherAuthorizationMySqlIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("edu_teacher_authorization_it")
            .withUsername("edu_teacher_authorization_it")
            .withPassword("edu_teacher_authorization_it_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
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
    private GraphVersionService graphVersionService;
    @Autowired
    private EvidenceImportService evidenceImportService;
    @Autowired
    private TeacherAnalyticsService teacherAnalyticsService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CurrentUser systemAdmin;
    private CurrentUser teachingAdmin;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private CurrentUser studentA;
    private CurrentUser studentB;
    private long courseA;
    private long courseB;
    private long pointA;
    private long pointB;
    private long exerciseB;
    private long questionB;
    private long graphA;
    private long graphB;
    private long evidenceB;
    private long evidenceImportRunB;

    @BeforeEach
    void createIsolationFixture() {
        int number = SEQUENCE.incrementAndGet();
        systemAdmin = currentUser(createUser("sec-system-" + number, "System", "SYSTEM_ADMIN"), "SYSTEM_ADMIN");
        teachingAdmin = currentUser(createUser("sec-teach-admin-" + number, "Teaching Admin", "TEACH_ADMIN"), "TEACH_ADMIN");
        teacherA = currentUser(createUser("sec-teacher-a-" + number, "Teacher A", "TEACHER"), "TEACHER");
        teacherB = currentUser(createUser("sec-teacher-b-" + number, "Teacher B", "TEACHER"), "TEACHER");
        studentA = currentUser(createUser("sec-student-a-" + number, "Student A", "STUDENT"), "STUDENT");
        studentB = currentUser(createUser("sec-student-b-" + number, "Student B", "STUDENT"), "STUDENT");

        courseA = courseService.create(new CourseApi.CourseRequest(
                "SEC-A-" + number, "Course A", "Teacher A only", "ACTIVE"
        )).id();
        courseB = courseService.create(new CourseApi.CourseRequest(
                "SEC-B-" + number, "Course B", "Teacher B only", "ACTIVE"
        )).id();
        teacherAssignmentService.assign(courseA,
                new TeacherAssignmentApi.CreateAssignmentRequest(teacherA.id(), TeacherAssignmentRole.OWNER), systemAdmin);
        teacherAssignmentService.assign(courseB,
                new TeacherAssignmentApi.CreateAssignmentRequest(teacherB.id(), TeacherAssignmentRole.INSTRUCTOR), systemAdmin);
        courseService.enroll(courseA, studentA);
        courseService.enroll(courseB, studentB);

        pointA = createPoint(courseA, "SEC-A-P-" + number, "Course A Point");
        long pointB2 = createPoint(courseB, "SEC-B-P2-" + number, "Course B Point 2");
        pointB = createPoint(courseB, "SEC-B-P-" + number, "Course B Point");
        createQuestion(courseA, pointA, "SEC-A-EX-" + number, "Course A question");
        exerciseB = createExercise(courseB, pointB, "SEC-B-EX-" + number, "Course B exercise", "sec-b-source-" + number);
        long targetExerciseB = createExercise(courseB, pointB2, "SEC-B-EX2-" + number, "Course B target exercise", "sec-b-target-" + number);
        questionB = questionService.create(new QuestionApi.QuestionRequest(
                exerciseB, "SINGLE_CHOICE", "Course B question", List.of("A"), "A is correct", null, "ACTIVE",
                List.of(new QuestionApi.OptionRequest("A", "Correct", 1), new QuestionApi.OptionRequest("B", "Incorrect", 2))
        ), systemAdmin.id()).id();
        graphA = graphVersionService.create(new GraphApi.CreateGraphVersionRequest(courseA, "Course A draft", false), systemAdmin.id()).id();
        graphB = graphVersionService.create(new GraphApi.CreateGraphVersionRequest(courseB, "Course B draft", false), systemAdmin.id()).id();
        evidenceImportRunB = evidenceImportService.apply(graphB, new GraphApi.EvidenceImportRequest(List.of(
                new GraphApi.RawPrerequisiteEvidenceRequest(
                        "sec-evidence-" + number, "sec-b-source-" + number, "sec-b-target-" + number, java.util.Map.of()
                )
        )), systemAdmin.id()).importRunId();
        evidenceB = evidenceImportService.listEvidence(courseB).getFirst().id();
    }

    @Test
    void teacherACannotUseGuessedCrossCourseIdsAcrossAllExistingAndNewTeachingPaths() throws Exception {
        mockMvc.perform(get("/api/v1/courses/{courseId}", courseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/courses/{courseId}/knowledge-points", courseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exercise-units/{exerciseUnitId}", exerciseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exercise-units/{exerciseUnitId}/questions/next", exerciseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/courses/{courseId}/graph", courseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teacher/courses/{courseId}/analytics/overview", courseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teacher/courses/{courseId}/students/{studentId}/analytics", courseA, studentB.id())
                        .with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/questions/{questionId}/answers", questionB).with(principal(studentA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectedOptionKeys": ["B"], "durationMs": 0, "clientRequestId": "cross-course-student"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/knowledge-points").with(principal(teacherA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId": %d, "areaId": null, "knowledgeCode": "FORBIDDEN", "knowledgeName": "Forbidden",
                                 "sourceType": "PLATFORM", "externalId": null, "mappingStatus": "MAPPED", "status": "ACTIVE"}
                                """.formatted(courseB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/exercise-units/{exerciseUnitId}/knowledge-points", exerciseB).with(principal(teacherA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledgePointId": %d, "mappingSource": "MANUAL", "confidence": 1.0, "verified": true}
                                """.formatted(pointB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/questions/{questionId}", questionB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/graph-versions/{graphVersionId}", graphB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/graph-versions/{graphVersionId}/evidence-reresolutions/dry-run", graphB)
                        .with(principal(teacherA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evidenceIds": [%d], "triggerType": "ADMIN_REQUEST"}
                                """.formatted(evidenceB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/evidence/{evidenceId}/resolution-history", evidenceB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/evidence-imports/{importRunId}/conflicts", evidenceImportRunB)
                        .with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/courses/{courseId}", courseB).with(principal(teacherA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/teachers", courseB).with(principal(teacherA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId": %d, "assignmentRole": "INSTRUCTOR"}
                                """.formatted(teacherA.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformTeachingAdminsAreAllowedStudentsAreDeniedAndInactiveRecordsAreExcluded() throws Exception {
        mockMvc.perform(get("/api/v1/teacher/courses/{courseId}/analytics/overview", courseA).with(principal(teacherA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/graph-versions/{graphVersionId}", graphA).with(principal(teacherA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teacher/courses/{courseId}/analytics/overview", courseB).with(principal(teachingAdmin)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/teachers", courseB).with(principal(systemAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId": %d, "assignmentRole": "INSTRUCTOR"}
                                """.formatted(teacherB.id())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teacher/courses").with(principal(studentA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/knowledge-points?courseId={courseId}", courseA).with(principal(teacherA)))
                .andExpect(status().isOk());

        teacherAssignmentService.update(courseA, teacherA.id(), new TeacherAssignmentApi.UpdateAssignmentRequest(
                TeacherAssignmentRole.OWNER, TeacherAssignmentStatus.INACTIVE
        ), systemAdmin);
        mockMvc.perform(get("/api/v1/teacher/courses/{courseId}/analytics/overview", courseA).with(principal(teacherA)))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE course_enrollment SET status = 'INACTIVE' WHERE course_id = ? AND student_id = ?", courseA, studentA.id());
        assertThat(teacherAnalyticsService.heatmap(courseA, 0, 50, systemAdmin).totalStudents()).isZero();
    }

    @Test
    void platformTeachingAdminRejectsInvalidAssignmentTargetsAndPayloads() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/teachers", courseB).with(principal(systemAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId": 999999, "assignmentRole": "INSTRUCTOR"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/teachers", courseB).with(principal(systemAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId": %d, "assignmentRole": "INSTRUCTOR"}
                                """.formatted(studentA.id())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/teachers", courseB).with(principal(systemAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId": %d, "assignmentRole": "UNSUPPORTED"}
                                """.formatted(teacherB.id())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/courses/{courseId}/teachers", 999999).with(principal(systemAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId": %d, "assignmentRole": "INSTRUCTOR"}
                                """.formatted(teacherB.id())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teacher/courses/{courseId}/analytics/overview", 999999).with(principal(systemAdmin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/admin/courses/{courseId}/teachers/{teacherId}", courseB, teacherB.id())
                        .with(principal(systemAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignmentRole": "INSTRUCTOR", "status": "UNSUPPORTED"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor principal(CurrentUser user) {
        return jwt()
                .jwt(token -> token
                        .claim("user_id", String.valueOf(user.id()))
                        .claim("username", user.username())
                        .claim("roles", user.roles().stream().sorted().toList()))
                .authorities(user.roles().stream()
                        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList());
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

    private long createExercise(long courseId, long pointId, String code, String name, String externalId) {
        ExerciseApi.ExerciseUnitResponse exercise = exerciseUnitService.create(new ExerciseApi.ExerciseUnitRequest(
                courseId, code, name, "PLATFORM", externalId, "RESOLVED", "UNMAPPED", null, "ACTIVE"
        ));
        exerciseUnitService.upsertMapping(exercise.id(), new ExerciseApi.MappingRequest(
                pointId, "MANUAL", BigDecimal.ONE, true
        ));
        return exercise.id();
    }

    private void createQuestion(long courseId, long pointId, String code, String stem) {
        long exercise = createExercise(courseId, pointId, code, stem + " exercise", null);
        questionService.create(new QuestionApi.QuestionRequest(
                exercise, "SINGLE_CHOICE", stem, List.of("A"), "A is correct", null, "ACTIVE",
                List.of(new QuestionApi.OptionRequest("A", "Correct", 1), new QuestionApi.OptionRequest("B", "Incorrect", 2))
        ), systemAdmin.id());
    }
}
