package com.smartlearning.course.infrastructure.persistence;

import com.smartlearning.course.domain.CourseTeacherAssignment;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseTeacherAssignmentRepository extends JpaRepository<CourseTeacherAssignment, Long> {

    boolean existsByTeacherIdAndCourseIdAndStatus(Long teacherId, Long courseId, TeacherAssignmentStatus status);

    Optional<CourseTeacherAssignment> findByTeacherIdAndCourseId(Long teacherId, Long courseId);

    List<CourseTeacherAssignment> findByCourseIdOrderByTeacherIdAsc(Long courseId);

    List<CourseTeacherAssignment> findByTeacherIdAndStatusOrderByCourseIdAsc(Long teacherId, TeacherAssignmentStatus status);
}
