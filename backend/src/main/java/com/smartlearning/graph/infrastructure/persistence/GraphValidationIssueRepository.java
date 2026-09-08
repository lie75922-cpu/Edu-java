package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.GraphValidationIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GraphValidationIssueRepository extends JpaRepository<GraphValidationIssue, Long> {

    List<GraphValidationIssue> findByGraphVersionIdOrderByIdAsc(Long graphVersionId);

    @Transactional
    long deleteByGraphVersionId(Long graphVersionId);
}
