package com.smartlearning.mastery.application;

import com.smartlearning.outbox.domain.OutboxEvent;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Consumes only MASTERY_UPDATE_REQUEST; graph projection events are intentionally invisible here. */
@Component
public class MasteryUpdateWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final MasteryUpdateEventProcessor processor;
    private final MasteryOutboxFailureRecorder failureRecorder;

    public MasteryUpdateWorker(
            OutboxEventRepository outboxEventRepository,
            MasteryUpdateEventProcessor processor,
            MasteryOutboxFailureRecorder failureRecorder
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.processor = processor;
        this.failureRecorder = failureRecorder;
    }

    @Scheduled(
            initialDelayString = "${app.mastery-worker.initial-delay-ms:30000}",
            fixedDelayString = "${app.mastery-worker.poll-interval-ms:5000}"
    )
    public void pollMasteryUpdates() {
        processNext();
    }

    public boolean processNext() {
        OutboxEvent event = outboxEventRepository
                .findFirstByEventTypeAndStatusOrderByIdAsc(MasteryUpdateEventProcessor.EVENT_TYPE, "PENDING")
                .orElse(null);
        if (event == null) {
            return false;
        }
        try {
            processor.process(event.getId());
        } catch (RuntimeException ex) {
            failureRecorder.record(event.getId(), safeFailureReason(ex));
        }
        return true;
    }

    private String safeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        String reason = exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() > 1500 ? reason.substring(0, 1500) : reason;
    }
}
