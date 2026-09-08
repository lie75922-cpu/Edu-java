package com.smartlearning.mastery.infrastructure.persistence;

import com.smartlearning.mastery.domain.MasteryProcessedAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasteryProcessedAnswerRepository extends JpaRepository<MasteryProcessedAnswer, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO mastery_processed_answer (answer_record_id, algorithm_version, processed_at)
            VALUES (:answerRecordId, :algorithmVersion, CURRENT_TIMESTAMP(3))
            """, nativeQuery = true)
    int claimAnswerRecord(
            @Param("answerRecordId") long answerRecordId,
            @Param("algorithmVersion") String algorithmVersion
    );
}
