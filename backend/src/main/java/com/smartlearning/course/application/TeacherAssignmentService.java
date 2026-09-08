package com.smartlearning.course.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.BadRequestException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.api.TeacherAssignmentApi;
import com.smartlearning.course.domain.CourseTeacherAssignment;
import com.smartlearning.course.domain.TeacherAssignmentRole;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import com.smartlearning.course.infrastructure.persistence.CourseTeacherAssignmentRepository;
import com.smartlearning.user.domain.PlatformUser;
import com.smartlearning.user.infrastructure.persistence.PlatformUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeacherAssignmentService {

    private final CourseService courseService;
    private final CourseAccessService courseAccessService;
    private final CourseTeacherAssignmentRepository assignmentRepository;
    private final PlatformUserRepository userRepository;

    public TeacherAssignmentService(
            CourseService courseService,
            CourseAccessService courseAccessService,
            CourseTeacherAssignmentRepository assignmentRepository,
            PlatformUserRepository userRepository
    ) {
        this.courseService = courseService;
        this.courseAccessService = courseAccessService;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
    }

    public List<TeacherAssignmentApi.AssignmentResponse> list(long courseId, CurrentUser requester) {
        courseAccessService.requirePlatformTeachingAdmin(requester);
        courseService.requireCourse(courseId);
        return assignmentRepository.findByCourseIdOrderByTeacherIdAsc(courseId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TeacherAssignmentApi.AssignmentResponse assign(
            long courseId,
            TeacherAssignmentApi.CreateAssignmentRequest request,
            CurrentUser requester
    ) {
        courseAccessService.requirePlatformTeachingAdmin(requester);
        courseService.requireCourse(courseId);
        PlatformUser teacher = requireTeachingUser(request.teacherId());
        CourseTeacherAssignment assignment = assignmentRepository.findByTeacherIdAndCourseId(teacher.getId(), courseId)
                .orElseGet(() -> new CourseTeacherAssignment(
                        teacher.getId(), courseId, request.assignmentRole(), requester.id()
                ));
        assignment.update(request.assignmentRole(), TeacherAssignmentStatus.ACTIVE, requester.id());
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public TeacherAssignmentApi.AssignmentResponse update(
            long courseId,
            long teacherId,
            TeacherAssignmentApi.UpdateAssignmentRequest request,
            CurrentUser requester
    ) {
        courseAccessService.requirePlatformTeachingAdmin(requester);
        courseService.requireCourse(courseId);
        if (request.status() == TeacherAssignmentStatus.ACTIVE) {
            requireTeachingUser(teacherId);
        }
        CourseTeacherAssignment assignment = assignmentRepository.findByTeacherIdAndCourseId(teacherId, courseId)
                .orElseThrow(() -> new NotFoundException("teacher assignment does not exist"));
        assignment.update(request.assignmentRole(), request.status(), requester.id());
        return toResponse(assignment);
    }

    @Transactional
    public void deactivate(long courseId, long teacherId, CurrentUser requester) {
        courseAccessService.requirePlatformTeachingAdmin(requester);
        courseService.requireCourse(courseId);
        CourseTeacherAssignment assignment = assignmentRepository.findByTeacherIdAndCourseId(teacherId, courseId)
                .orElseThrow(() -> new NotFoundException("teacher assignment does not exist"));
        assignment.deactivate(requester.id());
    }

    private PlatformUser requireTeachingUser(long teacherId) {
        PlatformUser teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("teacher user does not exist"));
        if (!"ACTIVE".equals(teacher.getStatus())) {
            throw new BadRequestException("teacher user is not active");
        }
        boolean hasTeachingRole = teacher.getRoles().stream().anyMatch(role ->
                "TEACHER".equals(role.getCode())
                        || "TEACH_ADMIN".equals(role.getCode())
                        || "SYSTEM_ADMIN".equals(role.getCode())
        );
        if (!hasTeachingRole) {
            throw new BadRequestException("assigned user must have a teaching role");
        }
        return teacher;
    }

    private TeacherAssignmentApi.AssignmentResponse toResponse(CourseTeacherAssignment assignment) {
        PlatformUser teacher = userRepository.findById(assignment.getTeacherId())
                .orElseThrow(() -> new IllegalStateException("teacher assignment references a missing user"));
        String displayName = teacher.getNickname() == null || teacher.getNickname().isBlank()
                ? teacher.getUsername()
                : teacher.getNickname();
        return new TeacherAssignmentApi.AssignmentResponse(
                assignment.getId(), assignment.getTeacherId(), teacher.getUsername(), displayName,
                assignment.getCourseId(), assignment.getAssignmentRole().name(), assignment.getStatus().name(),
                assignment.getAssignedBy(), assignment.getAssignedAt()
        );
    }
}
