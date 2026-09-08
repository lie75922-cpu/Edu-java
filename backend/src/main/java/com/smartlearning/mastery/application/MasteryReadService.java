package com.smartlearning.mastery.application;

import com.smartlearning.mastery.domain.MasteryStatus;
import com.smartlearning.mastery.domain.StudentKnowledgeMastery;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MasteryReadService {

    private final StudentKnowledgeMasteryRepository masteryRepository;

    public MasteryReadService(StudentKnowledgeMasteryRepository masteryRepository) {
        this.masteryRepository = masteryRepository;
    }

    public List<StudentKnowledgeMastery> observedForStudentAndCourse(long studentId, long courseId) {
        return masteryRepository.findByStudentIdAndCourseIdOrderByKnowledgePointIdAsc(studentId, courseId);
    }

    public Map<Long, MasteryState> statesFor(long studentId, long courseId, Collection<Long> knowledgePointIds) {
        Map<Long, MasteryState> states = new LinkedHashMap<>();
        for (Long pointId : knowledgePointIds) {
            states.put(pointId, MasteryState.unknown());
        }
        if (knowledgePointIds.isEmpty()) {
            return Map.copyOf(states);
        }
        masteryRepository.findByStudentIdAndCourseIdAndKnowledgePointIdIn(studentId, courseId, knowledgePointIds)
                .forEach(mastery -> states.put(mastery.getKnowledgePointId(), MasteryState.observed(mastery)));
        return Map.copyOf(states);
    }

    public record MasteryState(
            MasteryStatus status,
            BigDecimal masteryScore,
            int attemptCount,
            int correctCount,
            String sourceType,
            String algorithmVersion,
            Instant lastAnsweredAt,
            Instant updatedAt
    ) {
        public static MasteryState unknown() {
            return new MasteryState(MasteryStatus.UNKNOWN, null, 0, 0, null, null, null, null);
        }

        public static MasteryState observed(StudentKnowledgeMastery mastery) {
            return new MasteryState(
                    MasteryStatus.OBSERVED,
                    mastery.getMasteryScore(),
                    mastery.getAttemptCount(),
                    mastery.getCorrectCount(),
                    mastery.getSourceType(),
                    mastery.getAlgorithmVersion(),
                    mastery.getLastAnsweredAt(),
                    mastery.getUpdatedAt()
            );
        }

        public boolean isMastered(BigDecimal threshold) {
            return status == MasteryStatus.OBSERVED && masteryScore.compareTo(threshold) >= 0;
        }
    }
}
