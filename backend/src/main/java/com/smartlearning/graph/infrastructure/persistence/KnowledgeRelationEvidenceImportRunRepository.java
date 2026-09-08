package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidenceImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeRelationEvidenceImportRunRepository extends JpaRepository<KnowledgeRelationEvidenceImportRun, Long> {
}
