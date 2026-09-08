package com.smartlearning.assessment.infrastructure.persistence;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ExerciseKnowledgeRepository extends JpaRepository<ExerciseKnowledge, Long> {

    List<ExerciseKnowledge> findByExerciseUnitIdOrderByIdAsc(Long exerciseUnitId);

    Optional<ExerciseKnowledge> findByExerciseUnitIdAndKnowledgePointId(Long exerciseUnitId, Long knowledgePointId);

    long countByExerciseUnitId(Long exerciseUnitId);

    long countByKnowledgePointId(Long knowledgePointId);

    List<ExerciseKnowledge> findByKnowledgePointIdInOrderByKnowledgePointIdAscExerciseUnitIdAsc(Collection<Long> knowledgePointIds);
}
