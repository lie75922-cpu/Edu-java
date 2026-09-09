package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.RelationReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelation, Long> {

    long countByRelationSource(String relationSource);

    @Query("select count(relation) from KnowledgeRelation relation join GraphVersion version on version.id = relation.graphVersionId where version.status = :status")
    long countByGraphVersionStatus(@Param("status") com.smartlearning.graph.domain.GraphVersionStatus status);

    List<KnowledgeRelation> findByGraphVersionIdOrderByIdAsc(Long graphVersionId);

    List<KnowledgeRelation> findByGraphVersionIdAndReviewStatusOrderByIdAsc(Long graphVersionId, RelationReviewStatus reviewStatus);

    Optional<KnowledgeRelation> findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
            Long graphVersionId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String relationType
    );
}
