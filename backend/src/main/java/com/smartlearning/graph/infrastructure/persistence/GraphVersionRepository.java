package com.smartlearning.graph.infrastructure.persistence;

import com.smartlearning.graph.domain.GraphVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GraphVersionRepository extends JpaRepository<GraphVersion, Long> {

    List<GraphVersion> findByCourseIdOrderByVersionNoDesc(Long courseId);

    Optional<GraphVersion> findTopByCourseIdOrderByVersionNoDesc(Long courseId);
}
