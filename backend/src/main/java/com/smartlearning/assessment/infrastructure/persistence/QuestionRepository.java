package com.smartlearning.assessment.infrastructure.persistence;

import com.smartlearning.assessment.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findFirstByExerciseUnitIdAndStatusOrderByIdAsc(Long exerciseUnitId, String status);

    List<Question> findByExerciseUnitIdOrderByIdAsc(Long exerciseUnitId);
}
