package com.smartlearning.learning.infrastructure.persistence;

import com.smartlearning.learning.domain.AnswerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, Long> {

    Optional<AnswerRecord> findByStudentIdAndClientRequestId(Long studentId, String clientRequestId);

    long countByStudentIdAndQuestionId(Long studentId, Long questionId);

    List<AnswerRecord> findByStudentIdOrderByAnsweredAtDesc(Long studentId);

    List<AnswerRecord> findByStudentIdAndCourseIdAndAnsweredAtAfterOrderByAnsweredAtDesc(
            Long studentId,
            Long courseId,
            Instant answeredAfter
    );
}
