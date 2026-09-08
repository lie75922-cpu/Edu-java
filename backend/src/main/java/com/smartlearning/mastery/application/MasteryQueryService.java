package com.smartlearning.mastery.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import com.smartlearning.mastery.api.MasteryApi;
import com.smartlearning.mastery.domain.StudentKnowledgeMasteryHistory;
import com.smartlearning.mastery.infrastructure.persistence.StudentKnowledgeMasteryHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MasteryQueryService {

    private final CourseRepository courseRepository;
    private final CourseAccessService courseAccessService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentKnowledgeMasteryHistoryRepository historyRepository;
    private final MasteryReadService masteryReadService;

    public MasteryQueryService(
            CourseRepository courseRepository,
            CourseAccessService courseAccessService,
            KnowledgePointRepository knowledgePointRepository,
            StudentKnowledgeMasteryHistoryRepository historyRepository,
            MasteryReadService masteryReadService
    ) {
        this.courseRepository = courseRepository;
        this.courseAccessService = courseAccessService;
        this.knowledgePointRepository = knowledgePointRepository;
        this.historyRepository = historyRepository;
        this.masteryReadService = masteryReadService;
    }

    public MasteryApi.CourseMasteryResponse courseMastery(long courseId, CurrentUser user) {
        requireCourseAccess(courseId, user);
        List<KnowledgePoint> points = knowledgePointRepository.findByCourseIdAndStatusOrderByKnowledgeCodeAsc(courseId, "ACTIVE");
        Map<Long, MasteryReadService.MasteryState> states = masteryStates(user.id(), courseId,
                points.stream().map(KnowledgePoint::getId).toList());
        return new MasteryApi.CourseMasteryResponse(courseId, points.stream()
                .map(point -> toResponse(point, states.get(point.getId())))
                .toList());
    }

    public MasteryApi.MasteryResponse pointMastery(long knowledgePointId, CurrentUser user) {
        KnowledgePoint point = requirePoint(knowledgePointId);
        requireCourseAccess(point.getCourseId(), user);
        return toResponse(point, masteryStates(user.id(), point.getCourseId(), List.of(point.getId())).get(point.getId()));
    }

    public List<MasteryApi.MasteryHistoryResponse> history(long courseId, CurrentUser user) {
        requireCourseAccess(courseId, user);
        Map<Long, KnowledgePoint> pointsById = knowledgePointRepository.findByCourseIdAndStatusOrderByKnowledgeCodeAsc(courseId, "ACTIVE")
                .stream().collect(Collectors.toMap(KnowledgePoint::getId, point -> point));
        return historyRepository.findByStudentIdAndCourseIdOrderByCreatedAtDescIdDesc(user.id(), courseId).stream()
                .filter(history -> pointsById.containsKey(history.getKnowledgePointId()))
                .map(history -> toHistoryResponse(history, pointsById.get(history.getKnowledgePointId())))
                .toList();
    }

    public Map<Long, MasteryReadService.MasteryState> masteryStates(
            long studentId,
            long courseId,
            Collection<Long> knowledgePointIds
    ) {
        return masteryReadService.statesFor(studentId, courseId, knowledgePointIds);
    }

    private void requireCourseAccess(long courseId, CurrentUser user) {
        if (!courseRepository.existsById(courseId)) {
            throw new NotFoundException("course does not exist");
        }
        courseAccessService.requireCourseAccess(courseId, user);
    }

    private KnowledgePoint requirePoint(long knowledgePointId) {
        return knowledgePointRepository.findById(knowledgePointId)
                .orElseThrow(() -> new NotFoundException("knowledge point does not exist"));
    }

    private MasteryApi.MasteryResponse toResponse(KnowledgePoint point, MasteryReadService.MasteryState state) {
        return new MasteryApi.MasteryResponse(
                point.getId(), point.getKnowledgeCode(), point.getKnowledgeName(), state.status().name(),
                state.masteryScore(), state.attemptCount(), state.correctCount(), state.sourceType(),
                state.algorithmVersion(), state.lastAnsweredAt(), state.updatedAt()
        );
    }

    private MasteryApi.MasteryHistoryResponse toHistoryResponse(
            StudentKnowledgeMasteryHistory history,
            KnowledgePoint point
    ) {
        return new MasteryApi.MasteryHistoryResponse(
                history.getId(), point.getId(), point.getKnowledgeCode(), point.getKnowledgeName(),
                history.getAnswerRecordId(), history.getPreviousAttemptCount(), history.getPreviousCorrectCount(),
                history.getPreviousScore(), history.getNewAttemptCount(), history.getNewCorrectCount(),
                history.getNewScore(), history.getAlgorithmVersion(), history.getCreatedAt()
        );
    }
}
