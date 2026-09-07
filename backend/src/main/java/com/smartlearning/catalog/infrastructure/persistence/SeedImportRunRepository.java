package com.smartlearning.catalog.infrastructure.persistence;

import com.smartlearning.catalog.domain.SeedImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedImportRunRepository extends JpaRepository<SeedImportRun, Long> {
}
