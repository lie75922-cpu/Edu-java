package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidenceConflict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeRelationEvidenceConflictRepository extends JpaRepository<KnowledgeRelationEvidenceConflict, Long> {

    List<KnowledgeRelationEvidenceConflict> findByImportRunIdOrderByIdAsc(Long importRunId);
}
