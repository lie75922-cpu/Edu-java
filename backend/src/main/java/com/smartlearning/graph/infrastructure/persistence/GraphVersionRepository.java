package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.GraphVersion;
import com.smartlearning.graph.domain.GraphVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GraphVersionRepository extends JpaRepository<GraphVersion, Long> {

    long countByStatus(GraphVersionStatus status);

    List<GraphVersion> findByCourseIdOrderByVersionNoDesc(Long courseId);

    Optional<GraphVersion> findTopByCourseIdOrderByVersionNoDesc(Long courseId);
}
