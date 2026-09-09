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
 * 面向产品演示的合成业务数据。所有账号和教学数据均属于 Platform Business Domain，
 * 不读取 Junyi 匿名科研学生或 ProblemLog。DEMO-* 命名空间仅用于本地/评审演示。
 */
@Service
public class PlatformDemoSeedService {

    public static final String DEMO_PASSWORD = "LocalDemoOnly!2026";
    public static final String COURSE_A_CODE = "DM-101";
    public static final String COURSE_B_CODE = "DM-GRAPH-201";
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
        PlatformUser admin = ensureUser(ADMIN_USERNAME, "系统管理员", List.of(systemAdminRole));
        PlatformUser teacherA = ensureUser(TEACHER_A_USERNAME, "张老师", List.of(teacherRole));
        PlatformUser teacherB = ensureUser(TEACHER_B_USERNAME, "李老师", List.of(teacherRole));
        PlatformUser studentA = ensureUser(STUDENT_A_USERNAME, "陈晓同学", List.of(studentRole));
        PlatformUser studentB = ensureUser(STUDENT_B_USERNAME, "王晨同学", List.of(studentRole));
        PlatformUser studentC = ensureUser(STUDENT_C_USERNAME, "刘悦同学", List.of(studentRole));
        PlatformUser studentD = ensureUser(STUDENT_D_USERNAME, "赵宁同学", List.of(studentRole));

        Course discreteMath = ensureCourse(
                COURSE_A_CODE,
                "离散数学",
                "面向计算机类专业的核心基础课程，覆盖数理逻辑、集合论与关系、图论和代数结构，支持知识图谱导航、个性化学习与过程评价。"
        );
        Course graphWorkshop = ensureCourse(
                COURSE_B_CODE,
                "图论专题训练",
                "围绕路径、连通性、欧拉图、树等主题组织的拓展训练课程，用于课程级教师权限隔离演示。"
        );
        Course logicWorkshop = ensureCourse(
                "DM-LOGIC-201",
                "数理逻辑专题训练",
                "命题逻辑、范式与推理理论的强化训练。"
        );
        Course relationWorkshop = ensureCourse(
                "DM-REL-201",
                "集合与关系专题训练",
                "集合运算、二元关系、等价关系与偏序关系的专题训练。"
        );

        ensureAssignment(teacherA, discreteMath, admin, TeacherAssignmentRole.OWNER);
        ensureAssignment(teacherA, logicWorkshop, admin, TeacherAssignmentRole.INSTRUCTOR);
        ensureAssignment(teacherA, relationWorkshop, admin, TeacherAssignmentRole.INSTRUCTOR);
        ensureAssignment(teacherB, graphWorkshop, admin, TeacherAssignmentRole.OWNER);

        ensureEnrollment(studentA, discreteMath);
        ensureEnrollment(studentA, logicWorkshop);
        ensureEnrollment(studentA, relationWorkshop);
        ensureEnrollment(studentB, discreteMath);
        ensureEnrollment(studentC, discreteMath);
        ensureEnrollment(studentD, graphWorkshop);

        KnowledgeArea logic = ensureArea(discreteMath, "DM-01-LOGIC", "第一章 数理逻辑");
        KnowledgeArea setRelation = ensureArea(discreteMath, "DM-02-SET-REL", "第二章 集合论与关系");
        KnowledgeArea graph = ensureArea(discreteMath, "DM-03-GRAPH", "第三章 图论");
        KnowledgeArea algebra = ensureArea(discreteMath, "DM-04-ALGEBRA", "第四章 代数结构");

        KnowledgePoint proposition = ensurePoint(discreteMath, logic, "DM-LOGIC-PROP", "命题与逻辑联结词");
        KnowledgePoint equivalence = ensurePoint(discreteMath, logic, "DM-LOGIC-EQUIV", "命题等值演算");
        KnowledgePoint normalForm = ensurePoint(discreteMath, logic, "DM-LOGIC-NF", "析取范式与合取范式");
        KnowledgePoint inference = ensurePoint(discreteMath, logic, "DM-LOGIC-INFERENCE", "命题逻辑推理理论");

        KnowledgePoint setBasic = ensurePoint(discreteMath, setRelation, "DM-SET-BASIC", "集合与集合运算");
        KnowledgePoint relation = ensurePoint(discreteMath, setRelation, "DM-REL-BASIC", "二元关系及其性质");
        KnowledgePoint equivalenceRelation = ensurePoint(discreteMath, setRelation, "DM-REL-EQUIV", "等价关系与划分");
        KnowledgePoint partialOrder = ensurePoint(discreteMath, setRelation, "DM-REL-ORDER", "偏序关系与哈斯图");

        KnowledgePoint graphBasic = ensurePoint(discreteMath, graph, "DM-GRAPH-BASIC", "图的基本概念");
        KnowledgePoint connectivity = ensurePoint(discreteMath, graph, "DM-GRAPH-CONNECT", "路径、回路与连通性");
        KnowledgePoint euler = ensurePoint(discreteMath, graph, "DM-GRAPH-EULER", "欧拉图与欧拉回路");
        KnowledgePoint tree = ensurePoint(discreteMath, graph, "DM-GRAPH-TREE", "树与生成树");

        KnowledgePoint algebraSystem = ensurePoint(discreteMath, algebra, "DM-ALG-SYSTEM", "代数系统与运算");
        KnowledgePoint group = ensurePoint(discreteMath, algebra, "DM-ALG-GROUP", "群与子群");
        KnowledgePoint ring = ensurePoint(discreteMath, algebra, "DM-ALG-RING", "环与域");
        KnowledgePoint lattice = ensurePoint(discreteMath, algebra, "DM-ALG-LATTICE", "格与布尔代数");

        Map<String, KnowledgePoint> points = Map.ofEntries(
                Map.entry("prop", proposition), Map.entry("equiv", equivalence), Map.entry("nf", normalForm), Map.entry("infer", inference),
                Map.entry("set", setBasic), Map.entry("rel", relation), Map.entry("eqrel", equivalenceRelation), Map.entry("order", partialOrder),
                Map.entry("graph", graphBasic), Map.entry("conn", connectivity), Map.entry("euler", euler), Map.entry("tree", tree),
                Map.entry("alg", algebraSystem), Map.entry("group", group), Map.entry("ring", ring), Map.entry("lattice", lattice)
        );

        Map<String, ExerciseUnit> exercises = Map.ofEntries(
                Map.entry("prop", ensureExercise(discreteMath, "DM-EX-LOGIC-PROP", "命题与联结词基础练习")),
                Map.entry("equiv", ensureExercise(discreteMath, "DM-EX-LOGIC-EQUIV", "命题等值演算练习")),
                Map.entry("nf", ensureExercise(discreteMath, "DM-EX-LOGIC-NF", "范式转换练习")),
                Map.entry("infer", ensureExercise(discreteMath, "DM-EX-LOGIC-INFER", "逻辑推理练习")),
                Map.entry("set", ensureExercise(discreteMath, "DM-EX-SET", "集合运算练习")),
                Map.entry("rel", ensureExercise(discreteMath, "DM-EX-REL", "二元关系性质练习")),
                Map.entry("eqrel", ensureExercise(discreteMath, "DM-EX-EQREL", "等价关系练习")),
                Map.entry("order", ensureExercise(discreteMath, "DM-EX-ORDER", "偏序与哈斯图练习")),
                Map.entry("graph", ensureExercise(discreteMath, "DM-EX-GRAPH", "图的基本概念练习")),
                Map.entry("conn", ensureExercise(discreteMath, "DM-EX-CONN", "路径与连通性练习")),
                Map.entry("euler", ensureExercise(discreteMath, "DM-EX-EULER", "欧拉图练习")),
                Map.entry("tree", ensureExercise(discreteMath, "DM-EX-TREE", "树与生成树练习")),
                Map.entry("alg", ensureExercise(discreteMath, "DM-EX-ALG", "代数系统练习")),
                Map.entry("group", ensureExercise(discreteMath, "DM-EX-GROUP", "群与子群练习")),
                Map.entry("ring", ensureExercise(discreteMath, "DM-EX-RING", "环与域练习")),
                Map.entry("lattice", ensureExercise(discreteMath, "DM-EX-LATTICE", "格与布尔代数练习"))
        );

        for (Map.Entry<String, ExerciseUnit> entry : exercises.entrySet()) {
            ensureMapping(entry.getValue(), points.get(entry.getKey()));
        }

        ensureQuestion(exercises.get("prop"), admin, "设 p 为真、q 为假，则命题 p ∧ q 的真值是？", "B", "真", "假");
        ensureQuestion(exercises.get("equiv"), admin, "下列哪一个式子与 ¬(p ∧ q) 等值？", "A", "¬p ∨ ¬q", "¬p ∧ ¬q");
        ensureQuestion(exercises.get("nf"), admin, "主析取范式由哪一类基本项构成？", "A", "极小项的析取", "极大项的合取");
        ensureQuestion(exercises.get("infer"), admin, "由 p→q 与 p 可以推出 q，使用的是哪条推理规则？", "A", "假言推理", "拒取式");
        ensureQuestion(exercises.get("set"), admin, "若 A={1,2}, B={2,3}，则 A∩B 等于？", "A", "{2}", "{1,2,3}");
        ensureQuestion(exercises.get("rel"), admin, "关系 R 若满足 xRy 则 yRx，则称 R 具有？", "A", "对称性", "传递性");
        ensureQuestion(exercises.get("eqrel"), admin, "等价关系必须同时满足自反性、对称性和哪一性质？", "A", "传递性", "反对称性");
        ensureQuestion(exercises.get("order"), admin, "偏序关系除自反性和传递性外，还必须满足？", "B", "对称性", "反对称性");
        ensureQuestion(exercises.get("graph"), admin, "无向图中与顶点 v 关联的边数称为 v 的？", "A", "度", "距离");
        ensureQuestion(exercises.get("conn"), admin, "无向图中任意两顶点之间都存在路径，则该图称为？", "A", "连通图", "完全图");
        ensureQuestion(exercises.get("euler"), admin, "连通无向图存在欧拉回路的充要条件是？", "A", "所有顶点度数均为偶数", "所有顶点度数均为奇数");
        ensureQuestion(exercises.get("tree"), admin, "含 n 个顶点的树恰有多少条边？", "A", "n-1", "n");
        ensureQuestion(exercises.get("alg"), admin, "代数系统通常由非空集合以及定义在其上的什么组成？", "A", "运算", "路径");
        ensureQuestion(exercises.get("group"), admin, "群中的每个元素都必须存在？", "A", "逆元", "上界");
        ensureQuestion(exercises.get("ring"), admin, "环结构通常定义了几种二元运算？", "B", "一种", "两种");
        ensureQuestion(exercises.get("lattice"), admin, "布尔代数可看作一种具有补元的什么结构？", "A", "有界分配格", "自由群");

        // 第二门课程只承担教师跨课程隔离与多课程展示，不复制主课程知识资产。
        KnowledgeArea graphWorkshopArea = ensureArea(graphWorkshop, "GT-AREA", "图论专题");
        KnowledgePoint workshopPoint = ensurePoint(graphWorkshop, graphWorkshopArea, "GT-EULER", "欧拉图专题");
        ExerciseUnit workshopExercise = ensureExercise(graphWorkshop, "GT-EX-EULER", "欧拉图专题练习");
        ensureMapping(workshopExercise, workshopPoint);
        ensureQuestion(workshopExercise, admin, "一个连通无向图恰有两个奇度顶点时，它一定存在？", "B", "欧拉回路", "欧拉通路");

        if (discreteMath.getActiveGraphVersionId() == null) {
            GraphVersion graphVersion = graphVersionRepository.save(new GraphVersion(
                    discreteMath.getId(), 1, "《离散数学》核心知识先修关系演示图", admin.getId()
            ));
            addApprovedRelation(graphVersion, proposition, equivalence, admin);
            addApprovedRelation(graphVersion, equivalence, normalForm, admin);
            addApprovedRelation(graphVersion, normalForm, inference, admin);
            addApprovedRelation(graphVersion, setBasic, relation, admin);
            addApprovedRelation(graphVersion, relation, equivalenceRelation, admin);
            addApprovedRelation(graphVersion, relation, partialOrder, admin);
            addApprovedRelation(graphVersion, graphBasic, connectivity, admin);
            addApprovedRelation(graphVersion, connectivity, euler, admin);
            addApprovedRelation(graphVersion, connectivity, tree, admin);
            addApprovedRelation(graphVersion, algebraSystem, group, admin);
            addApprovedRelation(graphVersion, group, ring, admin);
            addApprovedRelation(graphVersion, algebraSystem, lattice, admin);

            GraphValidationService.ValidationOutcome validation = graphValidationService.validate(graphVersion.getId());
            if (validation.hasErrors()) {
                throw new IllegalStateException("《离散数学》演示知识图谱未通过校验");
            }
            graphVersionService.requestPublish(graphVersion.getId());
        }

        return new DemoContext(discreteMath.getId(), graphWorkshop.getId(), inference.getId(), admin.getId(), studentA.getId());
    }

    /** Runs after the core-data transaction commits so the projection worker sees the graph outbox event. */
    public void finalizeOperationalDemo(DemoContext context) {
        while (graphProjectionWorker.processNext()) {
            // Drain graph projection events only.
        }
        reprojectionService.rebuildActivePublishedGraphs();
        submitControlledAnswer(context);
        while (masteryUpdateWorker.processNext()) {
            // Drain mastery events only.
        }
        CurrentUser student = new CurrentUser(context.studentId(), STUDENT_A_USERNAME, Set.of("STUDENT"));
        if (recommendationSnapshotRepository.findFirstByStudentIdAndCourseIdOrderByGeneratedAtDescIdDesc(
                context.studentId(), context.courseAId()).isEmpty()) {
            recommendationService.generate(context.courseAId(), student);
        }
    }

    @Transactional
    void submitControlledAnswer(DemoContext context) {
        ExerciseUnit exercise = exerciseRepository.findByCourseIdAndExerciseCode(context.courseAId(), "DM-EX-LOGIC-PROP")
                .orElseThrow(() -> new IllegalStateException("命题逻辑演示练习缺失"));
        Question question = questionRepository.findFirstByExerciseUnitIdAndStatusOrderByIdAsc(exercise.getId(), "ACTIVE")
                .orElseThrow(() -> new IllegalStateException("命题逻辑演示题目缺失"));
        CurrentUser student = new CurrentUser(context.studentId(), STUDENT_A_USERNAME, Set.of("STUDENT"));
        // 故意提交错误答案，为薄弱知识、推荐和学习路径演示保留可观察状态。
        answerSubmissionService.submit(
                question.getId(),
                new AnswerApi.AnswerSubmissionRequest(List.of("A"), 500L, "DEMO-SEED-STUDENT-LOGIC-1"),
                student
        );
    }

    private void addApprovedRelation(GraphVersion version, KnowledgePoint source, KnowledgePoint target, PlatformUser admin) {
        relationRepository.save(new KnowledgeRelation(
                version.getId(), source.getId(), target.getId(), "PREREQUISITE", "MANUAL",
                new BigDecimal("1.0000"), 0, RelationReviewStatus.APPROVED, admin.getId()
        ));
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
                "本题用于《离散数学》产品演示，提交后系统会同步更新学习记录与掌握状态。",
                new BigDecimal("20.00"), "ACTIVE", createdBy.getId()
        ));
        optionRepository.saveAll(List.of(
                new QuestionOption(question.getId(), "A", optionA, 1),
                new QuestionOption(question.getId(), "B", optionB, 2)
        ));
    }

    public record DemoContext(long courseAId, long courseBId, long learningPathTargetKnowledgePointId, long adminId, long studentId) {
    }
}
