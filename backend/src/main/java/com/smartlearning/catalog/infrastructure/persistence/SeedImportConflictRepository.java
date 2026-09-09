package com.smartlearning.catalog.infrastructure.persistence;

import com.smartlearning.catalog.domain.SeedImportConflict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeedImportConflictRepository extends JpaRepository<SeedImportConflict, Long> {

    List<SeedImportConflict> findByImportRunIdOrderByIdAsc(Long importRunId);

    long countByImportRunId(Long importRunId);
}
