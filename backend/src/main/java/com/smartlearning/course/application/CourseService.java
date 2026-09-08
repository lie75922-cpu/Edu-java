package com.smartlearning.course.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ConflictException;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.domain.CourseEnrollment;
import com.smartlearning.course.infrastructure.persistence.CourseEnrollmentRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.course.infrastructure.persistence.CourseTeacherAssignmentRepository;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseAccessService courseAccessService;
    private final CourseTeacherAssignmentRepository assignmentRepository;

    public CourseService(
            CourseRepository courseRepository,
            CourseEnrollmentRepository enrollmentRepository,
            CourseAccessService courseAccessService,
            CourseTeacherAssignmentRepository assignmentRepository
    ) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseAccessService = courseAccessService;
        this.assignmentRepository = assignmentRepository;
    }

    public List<CourseApi.CourseResponse> listActiveCourses() {
        return courseRepository.findAllByStatusOrderByCourseCodeAsc("ACTIVE").stream().map(this::toResponse).toList();
    }

    public List<CourseApi.CourseResponse> listAccessibleCourses(CurrentUser user) {
        if (user.hasAnyRole("SYSTEM_ADMIN", "TEACH_ADMIN")) {
            return courseRepository.findAll().stream()
                    .sorted(Comparator.comparing(Course::getCourseCode).thenComparing(Course::getId))
                    .map(this::toResponse)
                    .toList();
        }
        if (user.hasAnyRole("TEACHER")) {
            List<Long> assignedCourseIds = assignmentRepository
                    .findByTeacherIdAndStatusOrderByCourseIdAsc(user.id(), TeacherAssignmentStatus.ACTIVE)
                    .stream()
                    .map(assignment -> assignment.getCourseId())
                    .toList();
            return courseRepository.findAllById(assignedCourseIds).stream()
                    .filter(course -> "ACTIVE".equals(course.getStatus()))
                    .sorted(Comparator.comparing(Course::getCourseCode).thenComparing(Course::getId))
                    .map(this::toResponse)
                    .toList();
        }
        if (user.hasAnyRole("STUDENT")) {
            List<Long> enrolledCourseIds = enrollmentRepository.findByStudentIdAndStatusOrderByCourseIdAsc(user.id(), "ACTIVE")
                    .stream()
                    .map(CourseEnrollment::getCourseId)
                    .toList();
            return courseRepository.findAllById(enrolledCourseIds).stream()
                    .filter(course -> "ACTIVE".equals(course.getStatus()))
                    .sorted(Comparator.comparing(Course::getCourseCode).thenComparing(Course::getId))
                    .map(this::toResponse)
                    .toList();
        }
        throw new ForbiddenOperationException("course access is required");
    }

    public CourseApi.CourseResponse getCourse(long courseId, CurrentUser user) {
        Course course = requireCourse(courseId);
        courseAccessService.requireCourseAccess(courseId, user);
        return toResponse(course);
    }

    @Transactional
    public CourseApi.EnrollmentResponse enroll(long courseId, CurrentUser user) {
        Course course = requireCourse(courseId);
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new ConflictException("course is not available for enrollment");
        }
        CourseEnrollment enrollment = enrollmentRepository.findByStudentIdAndCourseId(user.id(), courseId)
                .orElseGet(() -> enrollmentRepository.save(new CourseEnrollment(user.id(), courseId, "SELF_SERVICE")));
        return new CourseApi.EnrollmentResponse(enrollment.getId(), enrollment.getCourseId(), enrollment.getStatus(), enrollment.getEnrolledAt());
    }

    @Transactional
    public CourseApi.CourseResponse create(CourseApi.CourseRequest request) {
        if (courseRepository.existsByCourseCode(request.courseCode())) {
            throw new ConflictException("course code is already in use");
        }
        return toResponse(courseRepository.save(new Course(
                request.courseCode(), request.courseName(), request.description(), request.status()
        )));
    }

    @Transactional
    public CourseApi.CourseResponse update(long courseId, CourseApi.CourseRequest request) {
        Course course = requireCourse(courseId);
        courseRepository.findByCourseCode(request.courseCode())
                .filter(existing -> !existing.getId().equals(courseId))
                .ifPresent(existing -> {
                    throw new ConflictException("course code is already in use");
                });
        course.update(request.courseCode(), request.courseName(), request.description(), request.status());
        return toResponse(course);
    }

    @Transactional
    public void disable(long courseId) {
        requireCourse(courseId).disable();
    }

    public Course requireCourse(long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("course does not exist"));
    }

    private CourseApi.CourseResponse toResponse(Course course) {
        return new CourseApi.CourseResponse(
                course.getId(), course.getCourseCode(), course.getCourseName(), course.getDescription(), course.getStatus()
        );
    }
}
