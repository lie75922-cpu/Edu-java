package com.smartlearning.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "recommendation_snapshot")
public class RecommendationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "graph_version_id", nullable = false)
    private Long graphVersionId;

    @Column(name = "mastery_algorithm_version", nullable = false, length = 64)
    private String masteryAlgorithmVersion;

    @Column(name = "recommendation_rule_version", nullable = false, length = 64)
    private String recommendationRuleVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected RecommendationSnapshot() {
    }

    public RecommendationSnapshot(
            Long studentId,
            Long courseId,
            Long graphVersionId,
            String masteryAlgorithmVersion,
            String recommendationRuleVersion
    ) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.graphVersionId = graphVersionId;
        this.masteryAlgorithmVersion = masteryAlgorithmVersion;
        this.recommendationRuleVersion = recommendationRuleVersion;
        this.generatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getGraphVersionId() {
        return graphVersionId;
    }

    public String getMasteryAlgorithmVersion() {
        return masteryAlgorithmVersion;
    }

    public String getRecommendationRuleVersion() {
        return recommendationRuleVersion;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
