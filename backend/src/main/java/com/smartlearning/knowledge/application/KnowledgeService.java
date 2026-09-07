package com.smartlearning.knowledge.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.application.CourseAccessService;
import com.smartlearning.course.application.CourseService;
import com.smartlearning.knowledge.api.KnowledgeApi;
import com.smartlearning.knowledge.domain.KnowledgeArea;
import com.smartlearning.knowledge.domain.KnowledgePoint;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgeAreaRepository;
import com.smartlearning.knowledge.infrastructure.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeService {

    private final KnowledgeAreaRepository areaRepository;
    private final KnowledgePointRepository pointRepository;
    private final CourseService courseService;
    private final CourseAccessService courseAccessService;

    public KnowledgeService(
            KnowledgeAreaRepository areaRepository,
            KnowledgePointRepository pointRepository,
            CourseService courseService,
            CourseAccessService courseAccessService
    ) {
        this.areaRepository = areaRepository;
        this.pointRepository = pointRepository;
        this.courseService = courseService;
        this.courseAccessService = courseAccessService;
    }

    public List<KnowledgeApi.KnowledgePointResponse> listActivePoints(long courseId, CurrentUser user) {
        courseService.requireCourse(courseId);
        courseAccessService.requireCourseAccess(courseId, user);
        return pointRepository.findByCourseIdAndStatusOrderByKnowledgeCodeAsc(courseId, "ACTIVE").stream()
                .map(this::toPointResponse)
                .toList();
    }

    public List<KnowledgeApi.KnowledgeAreaResponse> listAreasForAdmin(long courseId) {
        courseService.requireCourse(courseId);
        return areaRepository.findByCourseIdAndStatusOrderByAreaCodeAsc(courseId, "ACTIVE").stream()
                .map(this::toAreaResponse)
                .toList();
    }

    public List<KnowledgeApi.KnowledgePointResponse> listPointsForAdmin(long courseId) {
        courseService.requireCourse(courseId);
        return pointRepository.findByCourseIdAndStatusOrderByKnowledgeCodeAsc(courseId, "ACTIVE").stream()
                .map(this::toPointResponse)
                .toList();
    }

    @Transactional
    public KnowledgeApi.KnowledgeAreaResponse createArea(KnowledgeApi.KnowledgeAreaRequest request) {
        courseService.requireCourse(request.courseId());
        if (areaRepository.existsByCourseIdAndAreaCode(request.courseId(), request.areaCode())) {
            throw new ConflictException("area code is already in use for this course");
        }
        return toAreaResponse(areaRepository.save(new KnowledgeArea(
                request.courseId(), request.areaCode(), request.areaName(), request.sourceType(), request.externalId(), request.status()
        )));
    }

    @Transactional
    public KnowledgeApi.KnowledgeAreaResponse updateArea(long areaId, KnowledgeApi.KnowledgeAreaRequest request) {
        courseService.requireCourse(request.courseId());
        KnowledgeArea area = requireArea(areaId);
        if ((!area.getCourseId().equals(request.courseId()) || !area.getAreaCode().equals(request.areaCode()))
                && areaRepository.existsByCourseIdAndAreaCode(request.courseId(), request.areaCode())) {
            throw new ConflictException("area code is already in use for this course");
        }
        area.update(request.courseId(), request.areaCode(), request.areaName(), request.sourceType(), request.externalId(), request.status());
        return toAreaResponse(area);
    }

    @Transactional
    public void disableArea(long areaId) {
        requireArea(areaId).disable();
    }

    @Transactional
    public KnowledgeApi.KnowledgePointResponse createPoint(KnowledgeApi.KnowledgePointRequest request) {
        validatePointReferences(request.courseId(), request.areaId());
        if (pointRepository.existsByCourseIdAndKnowledgeCode(request.courseId(), request.knowledgeCode())) {
            throw new ConflictException("knowledge code is already in use for this course");
        }
        return toPointResponse(pointRepository.save(new KnowledgePoint(
                request.courseId(), request.areaId(), request.knowledgeCode(), request.knowledgeName(), request.sourceType(),
                request.externalId(), request.mappingStatus(), request.status()
        )));
    }

    @Transactional
    public KnowledgeApi.KnowledgePointResponse updatePoint(long pointId, KnowledgeApi.KnowledgePointRequest request) {
        validatePointReferences(request.courseId(), request.areaId());
        KnowledgePoint point = requirePoint(pointId);
        if ((!point.getCourseId().equals(request.courseId()) || !point.getKnowledgeCode().equals(request.knowledgeCode()))
                && pointRepository.existsByCourseIdAndKnowledgeCode(request.courseId(), request.knowledgeCode())) {
            throw new ConflictException("knowledge code is already in use for this course");
        }
        point.update(
                request.courseId(), request.areaId(), request.knowledgeCode(), request.knowledgeName(), request.sourceType(),
                request.externalId(), request.mappingStatus(), request.status()
        );
        return toPointResponse(point);
    }

    @Transactional
    public void disablePoint(long pointId) {
        requirePoint(pointId).disable();
    }

    public KnowledgeArea requireArea(long areaId) {
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new NotFoundException("knowledge area does not exist"));
    }

    public KnowledgePoint requirePoint(long pointId) {
        return pointRepository.findById(pointId)
                .orElseThrow(() -> new NotFoundException("knowledge point does not exist"));
    }

    private void validatePointReferences(long courseId, Long areaId) {
        courseService.requireCourse(courseId);
        if (areaId != null && !requireArea(areaId).getCourseId().equals(courseId)) {
            throw new ConflictException("knowledge area must belong to the same course");
        }
    }

    private KnowledgeApi.KnowledgeAreaResponse toAreaResponse(KnowledgeArea area) {
        return new KnowledgeApi.KnowledgeAreaResponse(
                area.getId(), area.getCourseId(), area.getAreaCode(), area.getAreaName(), area.getSourceType(),
                area.getExternalId(), area.getStatus()
        );
    }

    private KnowledgeApi.KnowledgePointResponse toPointResponse(KnowledgePoint point) {
        return new KnowledgeApi.KnowledgePointResponse(
                point.getId(), point.getCourseId(), point.getAreaId(), point.getKnowledgeCode(), point.getKnowledgeName(),
                point.getSourceType(), point.getExternalId(), point.getMappingStatus(), point.getStatus()
        );
    }
}
