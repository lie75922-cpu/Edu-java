package com.smartlearning.course.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.common.exception.NotFoundException;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import com.smartlearning.course.infrastructure.persistence.CourseEnrollmentRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.course.infrastructure.persistence.CourseTeacherAssignmentRepository;
import org.springframework.stereotype.Service;

@Service
public class CourseAccessService {

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseTeacherAssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;

    public CourseAccessService(
            CourseEnrollmentRepository enrollmentRepository,
            CourseTeacherAssignmentRepository assignmentRepository,
            CourseRepository courseRepository
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.courseRepository = courseRepository;
    }

    public void requireCourseAccess(long courseId, CurrentUser user) {
        requireCourseReadAccess(courseId, user);
    }

    public void requireCourseReadAccess(long courseId, CurrentUser user) {
        Course course = requireCourse(courseId);
        if (user.hasAnyRole("SYSTEM_ADMIN", "TEACH_ADMIN")) {
            return;
        }
        if (user.hasAnyRole("TEACHER")) {
            requireActiveTeacherAssignment(course, user);
            return;
        }
        if (!user.hasAnyRole("STUDENT")) {
            throw new ForbiddenOperationException("course read access is required");
        }
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new ForbiddenOperationException("student access requires an active course");
        }
        if (!enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(user.id(), courseId, "ACTIVE")) {
            throw new ForbiddenOperationException("the user is not enrolled in this course");
        }
    }

    public void requireTeachingAccess(long courseId, CurrentUser user) {
        Course course = requireCourse(courseId);
        if (user.hasAnyRole("SYSTEM_ADMIN", "TEACH_ADMIN")) {
            return;
        }
        if (!user.hasAnyRole("TEACHER")) {
            throw new ForbiddenOperationException("teaching access is required for this course");
        }
        requireActiveTeacherAssignment(course, user);
    }

    public void requirePlatformTeachingAdmin(CurrentUser user) {
        if (!user.hasAnyRole("SYSTEM_ADMIN", "TEACH_ADMIN")) {
            throw new ForbiddenOperationException("platform teaching administrator access is required");
        }
    }

    public boolean hasActiveTeacherAssignment(long teacherId, long courseId) {
        return assignmentRepository.existsByTeacherIdAndCourseIdAndStatus(
                teacherId, courseId, TeacherAssignmentStatus.ACTIVE
        );
    }

    public void requireActiveEnrollmentForTeaching(long courseId, long studentId) {
        if (!enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(studentId, courseId, "ACTIVE")) {
            throw new ForbiddenOperationException("the student is not actively enrolled in this course");
        }
    }

    private Course requireCourse(long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("course does not exist"));
    }

    private void requireActiveTeacherAssignment(Course course, CurrentUser user) {
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new ForbiddenOperationException("teacher access requires an active course");
        }
        if (!hasActiveTeacherAssignment(user.id(), course.getId())) {
            throw new ForbiddenOperationException("the teacher is not assigned to this course");
        }
    }
}
