package com.smartlearning.assessment.infrastructure.persistence;

import com.smartlearning.assessment.domain.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionIdOrderBySortOrderAsc(Long questionId);
}
