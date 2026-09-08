package com.smartlearning.assessment.infrastructure.persistence;

import com.smartlearning.assessment.domain.ExerciseUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExerciseUnitRepository extends JpaRepository<ExerciseUnit, Long> {

    boolean existsByCourseIdAndExerciseCode(Long courseId, String exerciseCode);

    Optional<ExerciseUnit> findByCourseIdAndExerciseCode(Long courseId, String exerciseCode);

    List<ExerciseUnit> findByCourseIdAndStatusOrderByExerciseCodeAsc(Long courseId, String status);

    List<ExerciseUnit> findByCourseIdAndSourceTypeAndExternalId(Long courseId, String sourceType, String externalId);

    List<ExerciseUnit> findByIdInAndStatusOrderByExerciseCodeAsc(Collection<Long> ids, String status);
}
