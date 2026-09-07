package com.smartlearning.course.infrastructure.persistence;

import com.smartlearning.course.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCode(String courseCode);

    boolean existsByCourseCode(String courseCode);

    List<Course> findAllByStatusOrderByCourseCodeAsc(String status);
}
