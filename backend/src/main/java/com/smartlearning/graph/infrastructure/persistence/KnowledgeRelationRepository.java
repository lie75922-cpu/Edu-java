package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.KnowledgeRelation;
import com.smartlearning.graph.domain.RelationReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelation, Long> {

    List<KnowledgeRelation> findByGraphVersionIdOrderByIdAsc(Long graphVersionId);

    List<KnowledgeRelation> findByGraphVersionIdAndReviewStatusOrderByIdAsc(Long graphVersionId, RelationReviewStatus reviewStatus);

    Optional<KnowledgeRelation> findByGraphVersionIdAndSourceKnowledgePointIdAndTargetKnowledgePointIdAndRelationType(
            Long graphVersionId,
            Long sourceKnowledgePointId,
            Long targetKnowledgePointId,
            String relationType
    );
}
