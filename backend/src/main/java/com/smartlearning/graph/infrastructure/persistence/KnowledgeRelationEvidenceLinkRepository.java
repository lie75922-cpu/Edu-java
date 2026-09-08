package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelationEvidenceLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KnowledgeRelationEvidenceLinkRepository extends JpaRepository<KnowledgeRelationEvidenceLink, Long> {

    boolean existsByRelationIdAndEvidenceId(Long relationId, Long evidenceId);

    List<KnowledgeRelationEvidenceLink> findByRelationIdOrderByIdAsc(Long relationId);

    List<KnowledgeRelationEvidenceLink> findByRelationIdInOrderByIdAsc(Collection<Long> relationIds);

    long countByRelationId(Long relationId);

    @Query(value = """
            SELECT link.*
            FROM knowledge_relation_evidence_link link
            JOIN knowledge_relation relation ON relation.id = link.relation_id
            WHERE link.evidence_id = :evidenceId
              AND relation.graph_version_id = :graphVersionId
            ORDER BY link.id ASC
            """, nativeQuery = true)
    List<KnowledgeRelationEvidenceLink> findByEvidenceIdAndGraphVersionIdOrderByIdAsc(
            @Param("evidenceId") Long evidenceId,
            @Param("graphVersionId") Long graphVersionId
    );
}
