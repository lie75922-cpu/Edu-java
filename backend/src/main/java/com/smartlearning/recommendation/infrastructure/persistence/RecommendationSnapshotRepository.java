package com.smartlearning.recommendation.infrastructure.persistence;

import com.smartlearning.recommendation.domain.RecommendationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationSnapshotRepository extends JpaRepository<RecommendationSnapshot, Long> {

    Optional<RecommendationSnapshot> findFirstByStudentIdAndCourseIdOrderByGeneratedAtDescIdDesc(Long studentId, Long courseId);
}
