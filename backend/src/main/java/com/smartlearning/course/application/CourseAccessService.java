package com.smartlearning.course.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.course.infrastructure.persistence.CourseEnrollmentRepository;
import org.springframework.stereotype.Service;

@Service
public class CourseAccessService {

    private final CourseEnrollmentRepository enrollmentRepository;

    public CourseAccessService(CourseEnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    public void requireCourseAccess(long courseId, CurrentUser user) {
        if (user.hasAnyRole("SYSTEM_ADMIN", "TEACHER", "TEACH_ADMIN")) {
            return;
        }
        if (!enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(user.id(), courseId, "ACTIVE")) {
            throw new ForbiddenOperationException("the user is not enrolled in this course");
        }
    }
}
