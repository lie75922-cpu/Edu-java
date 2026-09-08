package com.smartlearning.outbox.infrastructure.persistence;

import com.smartlearning.outbox.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findFirstByEventTypeAndStatusOrderByIdAsc(String eventType, String status);
}
