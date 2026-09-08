package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidenceResolutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeRelationEvidenceResolutionHistoryRepository
        extends JpaRepository<KnowledgeRelationEvidenceResolutionHistory, Long> {

    List<KnowledgeRelationEvidenceResolutionHistory> findByEvidenceIdOrderByIdAsc(Long evidenceId);
}
