package com.smartlearning.mastery.application;

import com.smartlearning.assessment.domain.ExerciseKnowledge;
import com.smartlearning.assessment.domain.ExerciseUnit;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseKnowledgeRepository;
import com.smartlearning.assessment.infrastructure.persistence.ExerciseUnitRepository;
import com.smartlearning.learning.domain.AnswerRecord;
import com.smartlearning.learning.infrastructure.persistence.AnswerRecordRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.domain.MasteryProvider;
import com.smartlearning.mastery.domain.StudentKnowledgeMastery;
import com.smartlearning.mastery.domain.StudentKnowledgeMasteryHistory;
import com.smartlearning.mastery.infrastructure.persistence.MasteryProcessedAnswerRepository;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryHistoryRepository;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryRepository;
import com.smartlearning.outbox.domain.OutboxEvent;
import com.smartlearning.outbox.infrastructure.persistence.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MasteryUpdateEventProcessor {

    public static final String EVENT_TYPE = "MASTERY_UPDATE_REQUEST";

    private final OutboxEventRepository outboxEventRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final ExerciseUnitRepository exerciseUnitRepository;
    private final ExerciseKnowledgeRepository exerciseKnowledgeRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final MasteryProcessedAnswerRepository processedAnswerRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final StudentKnowledgeMasteryHistoryRepository historyRepository;
    private final MasteryProvider masteryProvider;

    public MasteryUpdateEventProcessor(
            OutboxEventRepository outboxEventRepository,
            AnswerRecordRepository answerRecordRepository,
            ExerciseUnitRepository exerciseUnitRepository,
            ExerciseKnowledgeRepository exerciseKnowledgeRepository,
            KnowledgePointRepository knowledgePointRepository,
            MasteryProcessedAnswerRepository processedAnswerRepository,
            StudentKnowledgeMasteryRepository masteryRepository,
            StudentKnowledgeMasteryHistoryRepository historyRepository,
            MasteryProvider masteryProvider
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.exerciseKnowledgeRepository = exerciseKnowledgeRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.processedAnswerRepository = processedAnswerRepository;
        this.masteryRepository = masteryRepository;
        this.historyRepository = historyRepository;
        this.masteryProvider = masteryProvider;
    }

    @Transactional
    public void process(long outboxEventId) {
        OutboxEvent event = outboxEventRepository.findById(outboxEventId).orElse(null);
        if (event == null || !EVENT_TYPE.equals(event.getEventType()) || !"PENDING".equals(event.getStatus())) {
            return;
        }

        AnswerRecord answer = resolveAnswer(event);
        if (answer == null) {
            return;
        }
        ExerciseUnit exercise = exerciseUnitRepository.findById(answer.getExerciseUnitId()).orElse(null);
        if (exercise == null) {
            fail(event, "answer record references a missing ExerciseUnit");
            return;
        }
        if (!exercise.getCourseId().equals(answer.getCourseId())) {
            fail(event, "answer record course does not match ExerciseUnit course");
            return;
        }

        List<ExerciseKnowledge> mappings = exerciseKnowledgeRepository.findByExerciseUnitIdOrderByIdAsc(exercise.getId());
        if (mappings.isEmpty()) {
            fail(event, "ExerciseUnit has no KnowledgePoint mapping; mastery was not fabricated");
            return;
        }
        List<KnowledgePoint> points = validateMappings(event, answer, mappings);
        if (points == null) {
            return;
        }

        int claimed = processedAnswerRepository.claimAnswerRecord(answer.getId(), masteryProvider.algorithmVersion());
        if (claimed == 0) {
            event.markDone();
            return;
        }

        Map<Long, KnowledgePoint> pointsById = new HashMap<>();
        points.forEach(point -> pointsById.put(point.getId(), point));
        for (ExerciseKnowledge mapping : mappings.stream().sorted(Comparator.comparing(ExerciseKnowledge::getKnowledgePointId)).toList()) {
            KnowledgePoint point = pointsById.get(mapping.getKnowledgePointId());
            masteryRepository.ensureMasteryRow(
                    answer.getStudentId(), answer.getCourseId(), point.getId(),
                    masteryProvider.sourceType(), masteryProvider.algorithmVersion()
            );
            StudentKnowledgeMastery mastery = masteryRepository.findForUpdate(answer.getStudentId(), point.getId())
                    .orElseThrow(() -> new IllegalStateException("mastery row was not created"));
            MasteryProvider.CurrentMastery previous = mastery.current();
            MasteryProvider.MasteryUpdate next = masteryProvider.update(previous, answer.isCorrect(), answer.getAnsweredAt());
            historyRepository.save(new StudentKnowledgeMasteryHistory(
                    answer.getStudentId(), answer.getCourseId(), point.getId(), answer.getId(), previous, next
            ));
            mastery.apply(next);
        }
        event.markDone();
    }

    private AnswerRecord resolveAnswer(OutboxEvent event) {
        long answerRecordId;
        try {
            answerRecordId = Long.parseLong(event.getAggregateId());
        } catch (NumberFormatException ex) {
            fail(event, "mastery event aggregate ID is not an answer record ID");
            return null;
        }
        AnswerRecord answer = answerRecordRepository.findById(answerRecordId).orElse(null);
        if (answer == null) {
            fail(event, "mastery event references a missing AnswerRecord");
        }
        return answer;
    }

    private List<KnowledgePoint> validateMappings(
            OutboxEvent event,
            AnswerRecord answer,
            List<ExerciseKnowledge> mappings
    ) {
        Map<Long, KnowledgePoint> points = new HashMap<>();
        knowledgePointRepository.findAllById(mappings.stream().map(ExerciseKnowledge::getKnowledgePointId).toList())
                .forEach(point -> points.put(point.getId(), point));
        if (points.size() != mappings.size()) {
            fail(event, "ExerciseUnit mapping references a missing KnowledgePoint");
            return null;
        }
        for (ExerciseKnowledge mapping : mappings) {
            KnowledgePoint point = points.get(mapping.getKnowledgePointId());
            if (!point.getCourseId().equals(answer.getCourseId())) {
                fail(event, "ExerciseUnit mapping crosses courses; mastery was not fabricated");
                return null;
            }
            if (!"ACTIVE".equals(point.getStatus())) {
                fail(event, "ExerciseUnit mapping targets an inactive KnowledgePoint; mastery was not fabricated");
                return null;
            }
        }
        return List.copyOf(points.values());
    }

    private void fail(OutboxEvent event, String reason) {
        event.markFailed(reason);
    }
}
