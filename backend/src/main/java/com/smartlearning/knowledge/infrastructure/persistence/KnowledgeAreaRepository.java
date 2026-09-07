package com.smartlearning.knowledge.infrastructure.persistence;

import com.smartlearning.knowledge.domain.KnowledgeArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeAreaRepository extends JpaRepository<KnowledgeArea, Long> {

    boolean existsByCourseIdAndAreaCode(Long courseId, String areaCode);

    List<KnowledgeArea> findByCourseIdAndStatusOrderByAreaCodeAsc(Long courseId, String status);

    List<KnowledgeArea> findByCourseIdAndSourceTypeAndExternalId(Long courseId, String sourceType, String externalId);
}
