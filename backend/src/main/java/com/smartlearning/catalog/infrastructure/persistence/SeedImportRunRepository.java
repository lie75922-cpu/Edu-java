package com.smartlearning.catalog.infrastructure.persistence;

import com.smartlearning.catalog.domain.SeedImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeedImportRunRepository extends JpaRepository<SeedImportRun, Long> {

    List<SeedImportRun> findAllByOrderByIdDesc();
}
