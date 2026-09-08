package com.smartlearning.mastery.infrastructure.persistence;

import com.smartlearning.mastery.domain.StudentKnowledgeMasteryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentKnowledgeMasteryHistoryRepository extends JpaRepository<StudentKnowledgeMasteryHistory, Long> {

    List<StudentKnowledgeMasteryHistory> findByStudentIdAndCourseIdOrderByCreatedAtDescIdDesc(Long studentId, Long courseId);
}
