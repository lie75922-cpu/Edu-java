package com.smartlearning.outbox.application;

import com.smartlearning.outbox.domain.OutboxEvent;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent enqueueMasteryUpdate(
            long answerRecordId,
            long studentId,
            long courseId,
            long questionId,
            long exerciseUnitId,
            boolean correct
    ) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "answerRecordId", answerRecordId,
                    "studentId", studentId,
                    "courseId", courseId,
                    "questionId", questionId,
                    "exerciseUnitId", exerciseUnitId,
                    "correct", correct
            ));
            return outboxEventRepository.save(new OutboxEvent(
                    "MASTERY_UPDATE_REQUEST", "ANSWER_RECORD", String.valueOf(answerRecordId), payload
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to serialize outbox event", ex);
        }
    }
}
