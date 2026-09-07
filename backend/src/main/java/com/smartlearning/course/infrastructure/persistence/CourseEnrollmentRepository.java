package com.smartlearning.course.infrastructure.persistence;

import com.smartlearning.course.domain.CourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, String status);

    Optional<CourseEnrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);
}
