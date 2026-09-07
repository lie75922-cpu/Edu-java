package com.smartlearning.outbox.infrastructure.persistence;

import com.smartlearning.outbox.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
