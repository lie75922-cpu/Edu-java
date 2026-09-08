package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidenceLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeRelationEvidenceLinkRepository extends JpaRepository<KnowledgeRelationEvidenceLink, Long> {

    boolean existsByRelationIdAndEvidenceId(Long relationId, Long evidenceId);

    List<KnowledgeRelationEvidenceLink> findByRelationIdOrderByIdAsc(Long relationId);

    List<KnowledgeRelationEvidenceLink> findByRelationIdInOrderByIdAsc(Collection<Long> relationIds);
}
