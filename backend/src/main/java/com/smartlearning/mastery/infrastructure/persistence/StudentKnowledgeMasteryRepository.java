package com.smartlearning.mastery.infrastructure.persistence;

import com.smartlearning.mastery.domain.StudentKnowledgeMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentKnowledgeMasteryRepository extends JpaRepository<StudentKnowledgeMastery, Long> {

    List<StudentKnowledgeMastery> findByStudentIdAndCourseIdOrderByKnowledgePointIdAsc(Long studentId, Long courseId);

    List<StudentKnowledgeMastery> findByStudentIdAndCourseIdAndKnowledgePointIdIn(Long studentId, Long courseId, Collection<Long> knowledgePointIds);

    Optional<StudentKnowledgeMastery> findByStudentIdAndKnowledgePointId(Long studentId, Long knowledgePointId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mastery from StudentKnowledgeMastery mastery
            where mastery.studentId = :studentId and mastery.knowledgePointId = :knowledgePointId
            """)
    Optional<StudentKnowledgeMastery> findForUpdate(
            @Param("studentId") Long studentId,
            @Param("knowledgePointId") Long knowledgePointId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO student_knowledge_mastery
                (student_id, course_id, knowledge_point_id, attempt_count, correct_count, mastery_score,
                 source_type, algorithm_version, last_answered_at, version)
            VALUES (:studentId, :courseId, :knowledgePointId, 0, 0, NULL,
                    :sourceType, :algorithmVersion, NULL, 0)
            """, nativeQuery = true)
    int ensureMasteryRow(
            @Param("studentId") long studentId,
            @Param("courseId") long courseId,
            @Param("knowledgePointId") long knowledgePointId,
            @Param("sourceType") String sourceType,
            @Param("algorithmVersion") String algorithmVersion
    );
}
