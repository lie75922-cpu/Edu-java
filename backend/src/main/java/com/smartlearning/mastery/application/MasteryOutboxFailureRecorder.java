package com.smartlearning.mastery.application;

import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasteryOutboxFailureRecorder {

    private final OutboxEventRepository outboxEventRepository;

    public MasteryOutboxFailureRecorder(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long eventId, String reason) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            if (MasteryUpdateEventProcessor.EVENT_TYPE.equals(event.getEventType()) && "PENDING".equals(event.getStatus())) {
                event.markFailed(reason);
            }
        });
    }
}
