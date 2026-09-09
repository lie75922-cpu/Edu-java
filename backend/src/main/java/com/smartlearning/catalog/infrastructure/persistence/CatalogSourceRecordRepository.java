package com.smartlearning.catalog.infrastructure.persistence;

import com.smartlearning.catalog.domain.CatalogSourceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CatalogSourceRecordRepository extends JpaRepository<CatalogSourceRecord, Long> {

    Optional<CatalogSourceRecord> findByCourseIdAndEntityTypeAndExternalId(
            Long courseId,
            String entityType,
            String externalId
    );
}
