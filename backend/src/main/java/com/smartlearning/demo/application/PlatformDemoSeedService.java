package com.smartlearning.demo.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.domain.Question;
import com.smartlearning.assessment.domain.QuestionOption;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionOptionRepository;
import com.smartlearning.assessment.infrastructure.persistence.QuestionRepository;
import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.domain.CourseEnrollment;
import com.smartlearning.course.domain.CourseTeacherAssignment;
import com.smartlearning.course.domain.TeacherAssignmentRole;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import com.smartlearning.course.infrastructure.persistence.CourseEnrollmentRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.course.infrastructure.persistence.CourseTeacherAssignmentRepository;
import com.smartlearning.graph.application.GraphProjectionWorker;
import com.smartlearning.graph.application.GraphValidationService;
import com.smartlearning.graph.application.GraphVersionService;
import com.smartlearning.graph.application.PublishedGraphReprojectionService;
import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.RelationReviewStatus;
import com.smartlearning.graph.infrastructure.persistence.GraphVersionRepository;
import com.smartlearning.graph.infrastructure.persistence.KnowledgeRelationRepository;
import com.smartlearning.knowledge.domain.KnowledgeArea;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgeAreaRepository;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.learning.api.AnswerApi;
import com.smartlearning.learning.application.AnswerSubmissionService;
import com.smartlearning.mastery.application.MasteryUpdateWorker;
import com.smartlearning.recommendation.application.RecommendationService;
import com.smartlearning.recommendation.infrastructure.persistence.RecommendationSnapshotRepository;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.domain.Role;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import com.smartlearning.user.infrastructure.persistence.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A deliberately small synthetic Platform Business Domain fixture. It never reads
 * Junyi research users or interaction data, and it only owns the DEMO-* namespace.
 */
@Service
public class PlatformDemoSeedService {

    public static final String DEMO_PASSWORD = "LocalDemoOnly!2026";
    public static final String COURSE_A_CODE = "DEMO-ALG-101";
    public static final String COURSE_B_CODE = "DEMO-GEO-201";
    public static final String ADMIN_USERNAME = "demo-admin";
    public static final String TEACHER_A_USERNAME = "demo-teacher-a";
    public static final String TEACHER_B_USERNAME = "demo-teacher-b";
    public static final String STUDENT_A_USERNAME = "demo-student-alice";
    public static final String STUDENT_B_USERNAME = "demo-student-bob";
    public static final String STUDENT_C_USERNAME = "demo-student-carol";
    public static final String STUDENT_D_USERNAME = "demo-student-dave";

    private final RoleRepository roleRepository;
    private final PlatformUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseTeacherAssignmentRepository assignmentRepository;
    private final KnowledgeAreaRepository areaRepository;
    private final KnowledgePointRepository pointRepository;
    private final ExerciseUnitRepository exerciseRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final GraphVersionRepository graphVersionRepository;
    private final KnowledgeRelationRepository relationRepository;
    private final GraphValidationService graphValidationService;
    private final GraphVersionService graphVersionService;
    private final GraphProjectionWorker graphProjectionWorker;
    private final PublishedGraphReprojectionService reprojectionService;
    private final AnswerSubmissionService answerSubmissionService;
    private final MasteryUpdateWorker masteryUpdateWorker;
    private final RecommendationService recommendationService;
    private final RecommendationSnapshotRepository recommendationSnapshotRepository;
    private final PasswordEncoder passwordEncoder;

    public PlatformDemoSeedService(
            RoleRepository roleRepository,
            PlatformUserRepository userRepository,
            CourseRepository courseRepository,
            CourseEnrollmentRepository enrollmentRepository,
            CourseTeacherAssignmentRepository assignmentRepository,
            KnowledgeAreaRepository areaRepository,
            KnowledgePointRepository pointRepository,
            ExerciseUnitRepository exerciseRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            QuestionRepository questionRepository,
            QuestionOptionRepository optionRepository,
            GraphVersionRepository graphVersionRepository,
            KnowledgeRelationRepository relationRepository,
            GraphValidationService graphValidationService,
            GraphVersionService graphVersionService,
            GraphProjectionWorker graphProjectionWorker,
            PublishedGraphReprojectionService reprojectionService,
            AnswerSubmissionService answerSubmissionService,
            MasteryUpdateWorker masteryUpdateWorker,
            RecommendationService recommendationService,
            RecommendationSnapshotRepository recommendationSnapshotRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.areaRepository = areaRepository;
        this.pointRepository = pointRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.graphVersionRepository = graphVersionRepository;
        this.relationRepository = relationRepository;
        this.graphValidationService = graphValidationService;
        this.graphVersionService = graphVersionService;
        this.graphProjectionWorker = graphProjectionWorker;
        this.reprojectionService = reprojectionService;
        this.answerSubmissionService = answerSubmissionService;
        this.masteryUpdateWorker = masteryUpdateWorker;
        this.recommendationService = recommendationService;
        this.recommendationSnapshotRepository = recommendationSnapshotRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DemoContext seedCoreData() {
        Role systemAdminRole = requireRole("SYSTEM_ADMIN");
        Role teacherRole = requireRole("TEACHER");
        Role studentRole = requireRole("STUDENT");
        PlatformUser admin = ensureUser(ADMIN_USERNAME, "Platform Demo Administrator", List.of(systemAdminRole));
        PlatformUser teacherA = ensureUser(TEACHER_A_USERNAME, "Teacher A", List.of(teacherRole));
        PlatformUser teacherB = ensureUser(TEACHER_B_USERNAME, "Teacher B", List.of(teacherRole));
        PlatformUser studentA = ensureUser(STUDENT_A_USERNAME, "Alice Demo Student", List.of(studentRole));
        PlatformUser studentB = ensureUser(STUDENT_B_USERNAME, "Bob Demo Student", List.of(studentRole));
        PlatformUser studentC = ensureUser(STUDENT_C_USERNAME, "Carol Demo Student", List.of(studentRole));
        PlatformUser studentD = ensureUser(STUDENT_D_USERNAME, "Dave Demo Student", List.of(studentRole));

        Course courseA = ensureCourse(COURSE_A_CODE, "Demo Algebra Foundations", "Synthetic Platform Business Domain course. No Junyi research users or records.");
        Course courseB = ensureCourse(COURSE_B_CODE, "Demo Geometry Studio", "Synthetic second course used to prove course-level teacher isolation.");
        ensureAssignment(teacherA, courseA, admin, TeacherAssignmentRole.OWNER);
        ensureAssignment(teacherB, courseB, admin, TeacherAssignmentRole.OWNER);
        ensureEnrollment(studentA, courseA);
        ensureEnrollment(studentB, courseA);
        ensureEnrollment(studentC, courseA);
        ensureEnrollment(studentD, courseB);

        KnowledgeArea algebraArea = ensureArea(courseA, "DEMO-ALG-AREA", "Algebra Foundations");
        KnowledgePoint foundation = ensurePoint(courseA, algebraArea, "DEMO-ALG-FOUNDATION", "Algebraic foundations");
        KnowledgePoint application = ensurePoint(courseA, algebraArea, "DEMO-ALG-APPLICATION", "Applied algebra");
        ExerciseUnit foundationExercise = ensureExercise(courseA, "DEMO-ALG-EX-FOUNDATION", "Foundation practice");
        ExerciseUnit applicationExercise = ensureExercise(courseA, "DEMO-ALG-EX-APPLICATION", "Applied practice");
        ensureMapping(foundationExercise, foundation);
        ensureMapping(applicationExercise, application);
        ensureQuestion(foundationExercise, admin, "Which expression evaluates to 4 when x = 2?", "A", "2 × x", "x + 1");
        ensureQuestion(applicationExercise, admin, "If y = 3x and x = 2, which value is y?", "B", "5", "6");

        KnowledgeArea geometryArea = ensureArea(courseB, "DEMO-GEO-AREA", "Geometry Studio");
        KnowledgePoint shapes = ensurePoint(courseB, geometryArea, "DEMO-GEO-SHAPES", "Shape properties");
        ExerciseUnit shapesExercise = ensureExercise(courseB, "DEMO-GEO-EX-SHAPES", "Shape practice");
        ensureMapping(shapesExercise, shapes);
        ensureQuestion(shapesExercise, admin, "How many sides does a triangle have?", "A", "3", "4");

        if (courseA.getActiveGraphVersionId() == null) {
            GraphVersion graphVersion = graphVersionRepository.save(new GraphVersion(
                    courseA.getId(), 1, "Synthetic Platform Demo prerequisite DAG", admin.getId()
            ));
            relationRepository.save(new KnowledgeRelation(
                    graphVersion.getId(), foundation.getId(), application.getId(), "PREREQUISITE", "MANUAL",
                    new BigDecimal("1.0000"), 0, RelationReviewStatus.APPROVED, admin.getId()
            ));
            GraphValidationService.ValidationOutcome validation = graphValidationService.validate(graphVersion.getId());
            if (validation.hasErrors()) {
                throw new IllegalStateException("synthetic Platform Demo graph did not validate");
            }
            graphVersionService.requestPublish(graphVersion.getId());
        }
        return new DemoContext(courseA.getId(), courseB.getId(), application.getId(), admin.getId(), studentA.getId());
    }

    /** Runs after the core-data transaction commits so the projection worker sees the graph outbox event. */
    public void finalizeOperationalDemo(DemoContext context) {
        while (graphProjectionWorker.processNext()) {
            // Drain only graph projection events; the worker's event-type query keeps mastery events separate.
        }
        reprojectionService.rebuildActivePublishedGraphs();
        submitControlledAnswer(context);
        while (masteryUpdateWorker.processNext()) {
            // Drain only mastery events; graph events remain invisible to this worker.
        }
        CurrentUser student = new CurrentUser(context.studentId(), STUDENT_A_USERNAME, Set.of("STUDENT"));
        if (recommendationSnapshotRepository.findFirstByStudentIdAndCourseIdOrderByGeneratedAtDescIdDesc(
                context.studentId(), context.courseAId()).isEmpty()) {
            recommendationService.generate(context.courseAId(), student);
        }
    }

    @Transactional
    void submitControlledAnswer(DemoContext context) {
        ExerciseUnit exercise = exerciseRepository.findByCourseIdAndExerciseCode(context.courseAId(), "DEMO-ALG-EX-FOUNDATION")
                .orElseThrow(() -> new IllegalStateException("synthetic foundation exercise is missing"));
        Question question = questionRepository.findFirstByExerciseUnitIdAndStatusOrderByIdAsc(exercise.getId(), "ACTIVE")
                .orElseThrow(() -> new IllegalStateException("synthetic foundation question is missing"));
        CurrentUser student = new CurrentUser(context.studentId(), STUDENT_A_USERNAME, Set.of("STUDENT"));
        answerSubmissionService.submit(
                question.getId(),
                new AnswerApi.AnswerSubmissionRequest(List.of("B"), 500L, "DEMO-SEED-ALICE-FOUNDATION-1"),
                student
        );
    }

    private Role requireRole(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("required platform role is missing"));
    }

    private PlatformUser ensureUser(String username, String nickname, List<Role> roles) {
        PlatformUser user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(new PlatformUser(username, passwordEncoder.encode(DEMO_PASSWORD), nickname)));
        for (Role role : roles) {
            if (user.getRoles().stream().noneMatch(existing -> existing.getCode().equals(role.getCode()))) {
                user.addRole(role);
            }
        }
        return userRepository.save(user);
    }

    private Course ensureCourse(String code, String name, String description) {
        return courseRepository.findByCourseCode(code)
                .orElseGet(() -> courseRepository.save(new Course(code, name, description, "ACTIVE")));
    }

    private void ensureEnrollment(PlatformUser student, Course course) {
        if (!enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(student.getId(), course.getId(), "ACTIVE")) {
            enrollmentRepository.save(new CourseEnrollment(student.getId(), course.getId(), "DEMO_SEED"));
        }
    }

    private void ensureAssignment(PlatformUser teacher, Course course, PlatformUser admin, TeacherAssignmentRole role) {
        CourseTeacherAssignment assignment = assignmentRepository.findByTeacherIdAndCourseId(teacher.getId(), course.getId())
                .orElseGet(() -> assignmentRepository.save(new CourseTeacherAssignment(teacher.getId(), course.getId(), role, admin.getId())));
        if (assignment.getStatus() != TeacherAssignmentStatus.ACTIVE || assignment.getAssignmentRole() != role) {
            assignment.update(role, TeacherAssignmentStatus.ACTIVE, admin.getId());
        }
    }

    private KnowledgeArea ensureArea(Course course, String code, String name) {
        return areaRepository.findByCourseIdAndAreaCode(course.getId(), code)
                .orElseGet(() -> areaRepository.save(new KnowledgeArea(
                        course.getId(), code, name, "PLATFORM_DEMO", code, "ACTIVE"
                )));
    }

    private KnowledgePoint ensurePoint(Course course, KnowledgeArea area, String code, String name) {
        return pointRepository.findByCourseIdAndKnowledgeCode(course.getId(), code)
                .orElseGet(() -> pointRepository.save(new KnowledgePoint(
                        course.getId(), area.getId(), code, name, "PLATFORM_DEMO", code, "MAPPED", "ACTIVE"
                )));
    }

    private ExerciseUnit ensureExercise(Course course, String code, String name) {
        return exerciseRepository.findByCourseIdAndExerciseCode(course.getId(), code)
                .orElseGet(() -> exerciseRepository.save(new ExerciseUnit(
                        course.getId(), code, name, "PLATFORM_DEMO", code, "RESOLVED", "MAPPED",
                        new BigDecimal("20.00"), "ACTIVE"
                )));
    }

    private void ensureMapping(ExerciseUnit exercise, KnowledgePoint point) {
        if (exerciseKnowledgeRepository.findByExerciseUnitIdAndKnowledgePointId(exercise.getId(), point.getId()).isEmpty()) {
            exerciseKnowledgeRepository.save(new ExerciseKnowledge(
                    exercise.getId(), point.getId(), "PLATFORM_DEMO", new BigDecimal("1.0000"), true
            ));
        }
    }

    private void ensureQuestion(
            ExerciseUnit exercise,
            PlatformUser createdBy,
            String stem,
            String correctOption,
            String optionA,
            String optionB
    ) {
        if (questionRepository.findFirstByExerciseUnitIdAndStatusOrderByIdAsc(exercise.getId(), "ACTIVE").isPresent()) {
            return;
        }
        Question question = questionRepository.save(new Question(
                exercise.getId(), "SINGLE_CHOICE", stem, "[\"" + correctOption + "\"]",
                "Synthetic Platform Demo explanation.", new BigDecimal("20.00"), "ACTIVE", createdBy.getId()
        ));
        optionRepository.saveAll(List.of(
                new QuestionOption(question.getId(), "A", optionA, 1),
                new QuestionOption(question.getId(), "B", optionB, 2)
        ));
    }

    public record DemoContext(long courseAId, long courseBId, long learningPathTargetKnowledgePointId, long adminId, long studentId) {
    }
}
