package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRelationEvidenceRepository extends JpaRepository<KnowledgeRelationEvidence, Long> {

    Optional<KnowledgeRelationEvidence> findByCourseIdAndSourceTypeAndExternalEvidenceId(
            Long courseId,
            String sourceType,
            String externalEvidenceId
    );

    List<KnowledgeRelationEvidence> findByCourseIdOrderByIdAsc(Long courseId);
}
