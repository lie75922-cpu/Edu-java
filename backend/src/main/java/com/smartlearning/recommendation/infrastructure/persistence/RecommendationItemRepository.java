package com.smartlearning.recommendation.infrastructure.persistence;

import com.smartlearning.recommendation.domain.RecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {

    List<RecommendationItem> findByRecommendationSnapshotIdOrderByRankNoAsc(Long recommendationSnapshotId);
}
