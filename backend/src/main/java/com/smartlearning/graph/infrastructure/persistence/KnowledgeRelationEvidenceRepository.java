package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRelationEvidenceRepository extends JpaRepository<KnowledgeRelationEvidence, Long> {

    Optional<KnowledgeRelationEvidence> findByCourseIdAndSourceTypeAndExternalEvidenceId(
            Long courseId,
            String sourceType,
            String externalEvidenceId
    );

    List<KnowledgeRelationEvidence> findByCourseIdOrderByIdAsc(Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evidence from KnowledgeRelationEvidence evidence where evidence.id = :evidenceId")
    Optional<KnowledgeRelationEvidence> findForUpdateById(@Param("evidenceId") Long evidenceId);
}
