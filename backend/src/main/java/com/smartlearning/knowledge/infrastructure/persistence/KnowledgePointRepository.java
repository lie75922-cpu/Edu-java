package com.smartlearning.knowledge.infrastructure.persistence;

import com.smartlearning.knowledge.domain.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, Long> {

    boolean existsByCourseIdAndKnowledgeCode(Long courseId, String knowledgeCode);

    List<KnowledgePoint> findByCourseIdAndStatusOrderByKnowledgeCodeAsc(Long courseId, String status);

    List<KnowledgePoint> findByCourseIdAndSourceTypeAndExternalId(Long courseId, String sourceType, String externalId);
}
