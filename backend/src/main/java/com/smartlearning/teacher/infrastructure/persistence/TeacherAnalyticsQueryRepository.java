package com.smartlearning.teacher.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Repository
public class TeacherAnalyticsQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeacherAnalyticsQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CourseRow> assignedCourses(long teacherId) {
        return jdbcTemplate.query("""
                SELECT c.id AS course_id, c.course_code, c.course_name, c.status,
                       a.assignment_role,
                       COUNT(DISTINCT enrollment.student_id) AS active_enrollment_count,
                       MAX(answer.answered_at) AS latest_answer_at
                FROM course_teacher_assignment a
                JOIN course c ON c.id = a.course_id
                LEFT JOIN course_enrollment enrollment
                       ON enrollment.course_id = c.id AND enrollment.status = 'ACTIVE'
                LEFT JOIN answer_record answer
                       ON answer.course_id = c.id
                WHERE a.teacher_id = ?
                  AND a.status = 'ACTIVE'
                  AND c.status = 'ACTIVE'
                GROUP BY c.id, c.course_code, c.course_name, c.status, a.assignment_role
                ORDER BY c.course_code ASC, c.id ASC
                """, (rs, rowNum) -> new CourseRow(
                rs.getLong("course_id"), rs.getString("course_code"), rs.getString("course_name"),
                rs.getString("status"), rs.getString("assignment_role"), rs.getLong("active_enrollment_count"),
                instant(rs, "latest_answer_at")
        ), teacherId);
    }

    public List<CourseRow> platformTeachingCourses() {
        return jdbcTemplate.query("""
                SELECT c.id AS course_id, c.course_code, c.course_name, c.status,
                       NULL AS assignment_role,
                       COUNT(DISTINCT enrollment.student_id) AS active_enrollment_count,
                       MAX(answer.answered_at) AS latest_answer_at
                FROM course c
                LEFT JOIN course_enrollment enrollment
                       ON enrollment.course_id = c.id AND enrollment.status = 'ACTIVE'
                LEFT JOIN answer_record answer
                       ON answer.course_id = c.id
                WHERE c.status = 'ACTIVE'
                GROUP BY c.id, c.course_code, c.course_name, c.status
                ORDER BY c.course_code ASC, c.id ASC
                """, (rs, rowNum) -> new CourseRow(
                rs.getLong("course_id"), rs.getString("course_code"), rs.getString("course_name"),
                rs.getString("status"), rs.getString("assignment_role"), rs.getLong("active_enrollment_count"),
                instant(rs, "latest_answer_at")
        ));
    }

    public long activeEnrollmentCount(long courseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM course_enrollment
                WHERE course_id = ? AND status = 'ACTIVE'
                """, Long.class, courseId);
    }

    public OverviewRow overview(long courseId, Instant from, Instant to) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(answer.id) AS attempt_count,
                       COALESCE(SUM(CASE WHEN answer.is_correct = TRUE THEN 1 ELSE 0 END), 0) AS correct_count,
                       COUNT(DISTINCT answer.student_id) AS students_with_activity,
                       MAX(answer.answered_at) AS latest_activity_at
                FROM answer_record answer
                JOIN course_enrollment enrollment
                  ON enrollment.course_id = answer.course_id
                 AND enrollment.student_id = answer.student_id
                 AND enrollment.status = 'ACTIVE'
                WHERE answer.course_id = ?
                  AND answer.answered_at >= ?
                  AND answer.answered_at < ?
                """, (rs, rowNum) -> new OverviewRow(
                rs.getLong("attempt_count"), rs.getLong("correct_count"), rs.getLong("students_with_activity"),
                instant(rs, "latest_activity_at")
        ), courseId, timestamp(from), timestamp(to));
    }

    public long activeKnowledgePointCount(long courseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM knowledge_point
                WHERE course_id = ? AND status = 'ACTIVE'
                """, Long.class, courseId);
    }

    public long observedMasteryStudentCount(long courseId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT mastery.student_id)
                FROM student_knowledge_mastery mastery
                JOIN course_enrollment enrollment
                  ON enrollment.course_id = mastery.course_id
                 AND enrollment.student_id = mastery.student_id
                 AND enrollment.status = 'ACTIVE'
                WHERE mastery.course_id = ?
                """, Long.class, courseId);
    }

    public List<String> masteryAlgorithmVersions(long courseId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT mastery.algorithm_version
                FROM student_knowledge_mastery mastery
                JOIN course_enrollment enrollment
                  ON enrollment.course_id = mastery.course_id
                 AND enrollment.student_id = mastery.student_id
                 AND enrollment.status = 'ACTIVE'
                WHERE mastery.course_id = ?
                ORDER BY mastery.algorithm_version ASC
                """, String.class, courseId);
    }

    public List<KnowledgePointRow> knowledgePointAnalytics(long courseId, Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT point.id AS knowledge_point_id,
                       point.knowledge_code,
                       point.knowledge_name,
                       COALESCE(mastery.observed_student_count, 0) AS observed_student_count,
                       mastery.mean_mastery,
                       COALESCE(mastery.weak_observed_student_count, 0) AS weak_observed_student_count,
                       mastery.algorithm_versions,
                       COALESCE(answer.attempt_count, 0) AS attempt_count,
                       COALESCE(answer.correct_count, 0) AS correct_count
                FROM knowledge_point point
                LEFT JOIN (
                    SELECT current_mastery.knowledge_point_id,
                           COUNT(DISTINCT current_mastery.student_id) AS observed_student_count,
                           AVG(current_mastery.mastery_score) AS mean_mastery,
                           SUM(CASE WHEN current_mastery.mastery_score < 0.7000 THEN 1 ELSE 0 END)
                               AS weak_observed_student_count,
                           GROUP_CONCAT(DISTINCT current_mastery.algorithm_version
                                        ORDER BY current_mastery.algorithm_version SEPARATOR '|') AS algorithm_versions
                    FROM student_knowledge_mastery current_mastery
                    JOIN course_enrollment enrollment
                      ON enrollment.course_id = current_mastery.course_id
                     AND enrollment.student_id = current_mastery.student_id
                     AND enrollment.status = 'ACTIVE'
                    WHERE current_mastery.course_id = ?
                    GROUP BY current_mastery.knowledge_point_id
                ) mastery ON mastery.knowledge_point_id = point.id
                LEFT JOIN (
                    SELECT mapping.knowledge_point_id,
                           COUNT(answer.id) AS attempt_count,
                           COALESCE(SUM(CASE WHEN answer.is_correct = TRUE THEN 1 ELSE 0 END), 0) AS correct_count
                    FROM answer_record answer
                    JOIN course_enrollment enrollment
                      ON enrollment.course_id = answer.course_id
                     AND enrollment.student_id = answer.student_id
                     AND enrollment.status = 'ACTIVE'
                    JOIN exercise_knowledge mapping ON mapping.exercise_unit_id = answer.exercise_unit_id
                    WHERE answer.course_id = ?
                      AND answer.answered_at >= ?
                      AND answer.answered_at < ?
                    GROUP BY mapping.knowledge_point_id
                ) answer ON answer.knowledge_point_id = point.id
                WHERE point.course_id = ?
                  AND point.status = 'ACTIVE'
                ORDER BY point.knowledge_code ASC, point.id ASC
                """, (rs, rowNum) -> new KnowledgePointRow(
                rs.getLong("knowledge_point_id"), rs.getString("knowledge_code"), rs.getString("knowledge_name"),
                rs.getLong("observed_student_count"), rs.getBigDecimal("mean_mastery"),
                rs.getLong("weak_observed_student_count"), rs.getString("algorithm_versions"),
                rs.getLong("attempt_count"), rs.getLong("correct_count")
        ), courseId, courseId, timestamp(from), timestamp(to), courseId);
    }

    public long activeHeatmapStudentCount(long courseId) {
        return activeEnrollmentCount(courseId);
    }

    public List<StudentRow> heatmapStudents(long courseId, int size, long offset) {
        return jdbcTemplate.query("""
                SELECT enrollment.student_id,
                       COALESCE(NULLIF(user.nickname, ''), user.username) AS display_name
                FROM course_enrollment enrollment
                JOIN sys_user user ON user.id = enrollment.student_id
                WHERE enrollment.course_id = ?
                  AND enrollment.status = 'ACTIVE'
                ORDER BY enrollment.student_id ASC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new StudentRow(rs.getLong("student_id"), rs.getString("display_name")),
                courseId, size, offset);
    }

    public List<KnowledgePointCatalogRow> activeKnowledgePoints(long courseId) {
        return jdbcTemplate.query("""
                SELECT id AS knowledge_point_id, knowledge_code, knowledge_name
                FROM knowledge_point
                WHERE course_id = ? AND status = 'ACTIVE'
                ORDER BY knowledge_code ASC, id ASC
                """, (rs, rowNum) -> new KnowledgePointCatalogRow(
                rs.getLong("knowledge_point_id"), rs.getString("knowledge_code"), rs.getString("knowledge_name")
        ), courseId);
    }

    public List<MasteryRow> masteryForHeatmapStudents(long courseId, Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", studentIds.stream().map(id -> "?").toList());
        List<Object> parameters = new ArrayList<>();
        parameters.add(courseId);
        parameters.addAll(studentIds);
        return jdbcTemplate.query("""
                SELECT student_id, knowledge_point_id, mastery_score, attempt_count, correct_count,
                       source_type, algorithm_version, last_answered_at, updated_at
                FROM student_knowledge_mastery
                WHERE course_id = ?
                  AND student_id IN (""" + placeholders + ")", (rs, rowNum) -> new MasteryRow(
                rs.getLong("student_id"), rs.getLong("knowledge_point_id"), rs.getBigDecimal("mastery_score"),
                rs.getInt("attempt_count"), rs.getInt("correct_count"), rs.getString("source_type"),
                rs.getString("algorithm_version"), instant(rs, "last_answered_at"), instant(rs, "updated_at")
        ), parameters.toArray());
    }

    public List<HighErrorQuestionRow> highErrorQuestions(
            long courseId,
            Instant from,
            Instant to,
            int minimumAttempts,
            int limit
    ) {
        return jdbcTemplate.query("""
                SELECT question.id AS question_id,
                       exercise.id AS exercise_unit_id,
                       exercise.exercise_code,
                       exercise.exercise_name,
                       question.stem,
                       COUNT(enrollment.student_id) AS attempt_count,
                       COALESCE(SUM(CASE WHEN enrollment.student_id IS NOT NULL AND answer.is_correct = FALSE THEN 1 ELSE 0 END), 0)
                           AS wrong_count,
                       COUNT(DISTINCT enrollment.student_id) AS distinct_students
                FROM question
                JOIN exercise_unit exercise ON exercise.id = question.exercise_unit_id
                LEFT JOIN answer_record answer
                  ON answer.question_id = question.id
                 AND answer.course_id = ?
                 AND answer.answered_at >= ?
                 AND answer.answered_at < ?
                LEFT JOIN course_enrollment enrollment
                  ON enrollment.course_id = answer.course_id
                 AND enrollment.student_id = answer.student_id
                 AND enrollment.status = 'ACTIVE'
                WHERE exercise.course_id = ?
                  AND exercise.status = 'ACTIVE'
                  AND question.status = 'ACTIVE'
                GROUP BY question.id, exercise.id, exercise.exercise_code, exercise.exercise_name, question.stem
                HAVING COUNT(enrollment.student_id) >= ?
                ORDER BY (COALESCE(SUM(CASE WHEN enrollment.student_id IS NOT NULL AND answer.is_correct = FALSE THEN 1 ELSE 0 END), 0)
                          / NULLIF(COUNT(enrollment.student_id), 0)) DESC,
                         wrong_count DESC,
                         question.id ASC
                LIMIT ?
                """, (rs, rowNum) -> new HighErrorQuestionRow(
                rs.getLong("question_id"), rs.getLong("exercise_unit_id"), rs.getString("exercise_code"),
                rs.getString("exercise_name"), rs.getString("stem"), rs.getLong("attempt_count"),
                rs.getLong("wrong_count"), rs.getLong("distinct_students")
        ), courseId, timestamp(from), timestamp(to), courseId, minimumAttempts, limit);
    }

    public Map<Long, List<AssociatedKnowledgePointRow>> associatedKnowledgePoints(Collection<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", questionIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT question.id AS question_id,
                       point.id AS knowledge_point_id,
                       point.knowledge_code,
                       point.knowledge_name
                FROM question
                JOIN exercise_knowledge mapping ON mapping.exercise_unit_id = question.exercise_unit_id
                JOIN knowledge_point point ON point.id = mapping.knowledge_point_id
                WHERE question.id IN (""" + placeholders + """
                )
                ORDER BY question.id ASC, point.knowledge_code ASC, point.id ASC
                """;
        return jdbcTemplate.query(sql, rs -> {
            java.util.LinkedHashMap<Long, List<AssociatedKnowledgePointRow>> grouped = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                grouped.computeIfAbsent(rs.getLong("question_id"), ignored -> new ArrayList<>()).add(
                        new AssociatedKnowledgePointRow(
                                rs.getLong("knowledge_point_id"), rs.getString("knowledge_code"), rs.getString("knowledge_name")
                        )
                );
            }
            return Map.copyOf(grouped);
        }, questionIds.toArray());
    }

    public StudentRow activeStudent(long courseId, long studentId) {
        List<StudentRow> students = jdbcTemplate.query("""
                SELECT enrollment.student_id,
                       COALESCE(NULLIF(user.nickname, ''), user.username) AS display_name
                FROM course_enrollment enrollment
                JOIN sys_user user ON user.id = enrollment.student_id
                WHERE enrollment.course_id = ?
                  AND enrollment.student_id = ?
                  AND enrollment.status = 'ACTIVE'
                """, (rs, rowNum) -> new StudentRow(rs.getLong("student_id"), rs.getString("display_name")), courseId, studentId);
        return students.isEmpty() ? null : students.getFirst();
    }

    public List<StudentMasteryRow> studentMastery(long courseId, long studentId) {
        return jdbcTemplate.query("""
                SELECT point.id AS knowledge_point_id,
                       point.knowledge_code,
                       point.knowledge_name,
                       mastery.mastery_score,
                       mastery.attempt_count,
                       mastery.correct_count,
                       mastery.source_type,
                       mastery.algorithm_version,
                       mastery.last_answered_at,
                       mastery.updated_at
                FROM knowledge_point point
                LEFT JOIN student_knowledge_mastery mastery
                  ON mastery.knowledge_point_id = point.id
                 AND mastery.course_id = ?
                 AND mastery.student_id = ?
                WHERE point.course_id = ?
                  AND point.status = 'ACTIVE'
                ORDER BY point.knowledge_code ASC, point.id ASC
                """, (rs, rowNum) -> new StudentMasteryRow(
                rs.getLong("knowledge_point_id"), rs.getString("knowledge_code"), rs.getString("knowledge_name"),
                rs.getBigDecimal("mastery_score"), nullableInteger(rs, "attempt_count"), nullableInteger(rs, "correct_count"),
                rs.getString("source_type"), rs.getString("algorithm_version"), instant(rs, "last_answered_at"), instant(rs, "updated_at")
        ), courseId, studentId, courseId);
    }

    public List<RecentAnswerRow> recentAnswers(long courseId, long studentId, int limit) {
        return jdbcTemplate.query("""
                SELECT answer.id AS answer_record_id,
                       answer.question_id,
                       answer.exercise_unit_id,
                       exercise.exercise_code,
                       exercise.exercise_name,
                       question.stem,
                       answer.is_correct,
                       answer.attempt_no,
                       answer.duration_ms,
                       answer.answered_at
                FROM answer_record answer
                JOIN question ON question.id = answer.question_id
                JOIN exercise_unit exercise ON exercise.id = answer.exercise_unit_id
                WHERE answer.course_id = ?
                  AND answer.student_id = ?
                ORDER BY answer.answered_at DESC, answer.id DESC
                LIMIT ?
                """, (rs, rowNum) -> new RecentAnswerRow(
                rs.getLong("answer_record_id"), rs.getLong("question_id"), rs.getLong("exercise_unit_id"),
                rs.getString("exercise_code"), rs.getString("exercise_name"), rs.getString("stem"),
                rs.getBoolean("is_correct"), rs.getInt("attempt_no"), nullableLong(rs, "duration_ms"),
                instant(rs, "answered_at")
        ), courseId, studentId, limit);
    }

    public List<MasteryHistoryRow> masteryHistory(long courseId, long studentId, int limit) {
        return jdbcTemplate.query("""
                SELECT history.id AS history_id,
                       history.knowledge_point_id,
                       point.knowledge_code,
                       point.knowledge_name,
                       history.answer_record_id,
                       history.previous_attempt_count,
                       history.previous_correct_count,
                       history.previous_score,
                       history.new_attempt_count,
                       history.new_correct_count,
                       history.new_score,
                       history.algorithm_version,
                       history.created_at
                FROM student_knowledge_mastery_history history
                JOIN knowledge_point point ON point.id = history.knowledge_point_id
                WHERE history.course_id = ?
                  AND history.student_id = ?
                ORDER BY history.created_at DESC, history.id DESC
                LIMIT ?
                """, (rs, rowNum) -> new MasteryHistoryRow(
                rs.getLong("history_id"), rs.getLong("knowledge_point_id"), rs.getString("knowledge_code"),
                rs.getString("knowledge_name"), rs.getLong("answer_record_id"), rs.getInt("previous_attempt_count"),
                rs.getInt("previous_correct_count"), rs.getBigDecimal("previous_score"), rs.getInt("new_attempt_count"),
                rs.getInt("new_correct_count"), rs.getBigDecimal("new_score"), rs.getString("algorithm_version"),
                instant(rs, "created_at")
        ), courseId, studentId, limit);
    }

    public ActivityRow activity(long courseId, long studentId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(id) AS attempt_count,
                       COALESCE(SUM(CASE WHEN is_correct = TRUE THEN 1 ELSE 0 END), 0) AS correct_count,
                       MAX(answered_at) AS latest_activity_at
                FROM answer_record
                WHERE course_id = ? AND student_id = ?
                """, (rs, rowNum) -> new ActivityRow(
                rs.getLong("attempt_count"), rs.getLong("correct_count"), instant(rs, "latest_activity_at")
        ), courseId, studentId);
    }

    public RecommendationSnapshotRow latestRecommendation(long courseId, long studentId) {
        List<RecommendationSnapshotRow> snapshots = jdbcTemplate.query("""
                SELECT id AS snapshot_id, graph_version_id, mastery_algorithm_version,
                       recommendation_rule_version, generated_at
                FROM recommendation_snapshot
                WHERE course_id = ? AND student_id = ?
                ORDER BY generated_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new RecommendationSnapshotRow(
                rs.getLong("snapshot_id"), rs.getLong("graph_version_id"), rs.getString("mastery_algorithm_version"),
                rs.getString("recommendation_rule_version"), instant(rs, "generated_at")
        ), courseId, studentId);
        return snapshots.isEmpty() ? null : snapshots.getFirst();
    }

    public List<RecommendationItemRow> recommendationItems(long snapshotId) {
        return jdbcTemplate.query("""
                SELECT item.rank_no,
                       item.knowledge_point_id,
                       point.knowledge_code,
                       point.knowledge_name,
                       item.exercise_unit_id,
                       exercise.exercise_code,
                       exercise.exercise_name,
                       item.mastery_score,
                       item.reason_code
                FROM recommendation_item item
                JOIN knowledge_point point ON point.id = item.knowledge_point_id
                LEFT JOIN exercise_unit exercise ON exercise.id = item.exercise_unit_id
                WHERE item.recommendation_snapshot_id = ?
                ORDER BY item.rank_no ASC
                """, (rs, rowNum) -> new RecommendationItemRow(
                rs.getInt("rank_no"), rs.getLong("knowledge_point_id"), rs.getString("knowledge_code"),
                rs.getString("knowledge_name"), nullableLong(rs, "exercise_unit_id"), rs.getString("exercise_code"),
                rs.getString("exercise_name"), rs.getBigDecimal("mastery_score"), rs.getString("reason_code")
        ), snapshotId);
    }

    private static LocalDateTime timestamp(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    public record CourseRow(
            long courseId,
            String courseCode,
            String courseName,
            String status,
            String assignmentRole,
            long activeEnrollmentCount,
            Instant latestAnswerAt
    ) {
    }

    public record OverviewRow(long attemptCount, long correctCount, long studentsWithActivity, Instant latestActivityAt) {
    }

    public record KnowledgePointRow(
            long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            long observedStudentCount,
            BigDecimal meanMastery,
            long weakObservedStudentCount,
            String algorithmVersions,
            long attemptCount,
            long correctCount
    ) {
    }

    public record StudentRow(long studentId, String displayName) {
    }

    public record KnowledgePointCatalogRow(long knowledgePointId, String knowledgeCode, String knowledgeName) {
    }

    public record MasteryRow(
            long studentId,
            long knowledgePointId,
            BigDecimal masteryScore,
            int attemptCount,
            int correctCount,
            String sourceType,
            String algorithmVersion,
            Instant lastAnsweredAt,
            Instant updatedAt
    ) {
    }

    public record HighErrorQuestionRow(
            long questionId,
            long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            String stem,
            long attemptCount,
            long wrongCount,
            long distinctStudents
    ) {
    }

    public record AssociatedKnowledgePointRow(long knowledgePointId, String knowledgeCode, String knowledgeName) {
    }

    public record StudentMasteryRow(
            long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            BigDecimal masteryScore,
            Integer attemptCount,
            Integer correctCount,
            String sourceType,
            String algorithmVersion,
            Instant lastAnsweredAt,
            Instant updatedAt
    ) {
    }

    public record RecentAnswerRow(
            long answerRecordId,
            long questionId,
            long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            String stem,
            boolean correct,
            int attemptNo,
            Long durationMs,
            Instant answeredAt
    ) {
    }

    public record MasteryHistoryRow(
            long historyId,
            long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            long answerRecordId,
            int previousAttemptCount,
            int previousCorrectCount,
            BigDecimal previousScore,
            int newAttemptCount,
            int newCorrectCount,
            BigDecimal newScore,
            String algorithmVersion,
            Instant createdAt
    ) {
    }

    public record ActivityRow(long attemptCount, long correctCount, Instant latestActivityAt) {
    }

    public record RecommendationSnapshotRow(
            long snapshotId,
            long graphVersionId,
            String masteryAlgorithmVersion,
            String recommendationRuleVersion,
            Instant generatedAt
    ) {
    }

    public record RecommendationItemRow(
            int rank,
            long knowledgePointId,
            String knowledgeCode,
            String knowledgeName,
            Long exerciseUnitId,
            String exerciseCode,
            String exerciseName,
            BigDecimal masteryScore,
            String reasonCode
    ) {
    }
}
