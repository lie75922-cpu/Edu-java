package com.smartlearning.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recommendation_item")
public class RecommendationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_snapshot_id", nullable = false)
    private Long recommendationSnapshotId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "knowledge_point_id", nullable = false)
    private Long knowledgePointId;

    @Column(name = "exercise_unit_id")
    private Long exerciseUnitId;

    @Column(name = "mastery_score", precision = 5, scale = 4)
    private BigDecimal masteryScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 32)
    private RecommendationReasonCode reasonCode;

    @Column(name = "explanation_json", nullable = false, columnDefinition = "JSON")
    private String explanationJson;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected RecommendationItem() {
    }

    public RecommendationItem(
            Long recommendationSnapshotId,
            int rankNo,
            Long knowledgePointId,
            Long exerciseUnitId,
            BigDecimal masteryScore,
            RecommendationReasonCode reasonCode,
            String explanationJson
    ) {
        this.recommendationSnapshotId = recommendationSnapshotId;
        this.rankNo = rankNo;
        this.knowledgePointId = knowledgePointId;
        this.exerciseUnitId = exerciseUnitId;
        this.masteryScore = masteryScore;
        this.reasonCode = reasonCode;
        this.explanationJson = explanationJson;
    }

    public Long getId() {
        return id;
    }

    public Long getRecommendationSnapshotId() {
        return recommendationSnapshotId;
    }

    public int getRankNo() {
        return rankNo;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public Long getExerciseUnitId() {
        return exerciseUnitId;
    }

    public BigDecimal getMasteryScore() {
        return masteryScore;
    }

    public RecommendationReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getExplanationJson() {
        return explanationJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
