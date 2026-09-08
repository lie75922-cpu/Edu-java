package com.smartlearning.mastery.application;

import com.smartlearning.mastery.domain.MasteryStatus;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasteryReadServiceTest {

    @Test
    void noAnswerHistoryIsUnknownRatherThanAnInventedHalfMastery() {
        StudentKnowledgeMasteryRepository repository = mock(StudentKnowledgeMasteryRepository.class);
        when(repository.findByStudentIdAndCourseIdAndKnowledgePointIdIn(any(), any(), any())).thenReturn(List.of());
        MasteryReadService service = new MasteryReadService(repository);

        Map<Long, MasteryReadService.MasteryState> states = service.statesFor(7L, 8L, List.of(101L));

        assertThat(states.get(101L).status()).isEqualTo(MasteryStatus.UNKNOWN);
        assertThat(states.get(101L).masteryScore()).isNull();
        assertThat(states.get(101L).attemptCount()).isZero();
        assertThat(states.get(101L).isMastered(new java.math.BigDecimal("0.70"))).isFalse();
    }
}
