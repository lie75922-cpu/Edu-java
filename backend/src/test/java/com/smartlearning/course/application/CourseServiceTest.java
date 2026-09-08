package com.smartlearning.course.application;

import com.smartlearning.course.api.CourseApi;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.infrastructure.persistence.CourseEnrollmentRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.course.infrastructure.persistence.CourseTeacherAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private CourseTeacherAssignmentRepository assignmentRepository;

    @Test
    void courseCreateUpdateAndDisableFollowSoftDeleteContract() {
        when(courseRepository.existsByCourseCode("MATH-1")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            ReflectionTestUtils.setField(course, "id", 7L);
            return course;
        });
        CourseService service = new CourseService(courseRepository, enrollmentRepository, courseAccessService, assignmentRepository);

        CourseApi.CourseResponse created = service.create(new CourseApi.CourseRequest("MATH-1", "Math", "Initial", "ACTIVE"));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course("MATH-1", "Math", "Initial", "ACTIVE")));
        Course course = courseRepository.findById(7L).orElseThrow();
        ReflectionTestUtils.setField(course, "id", 7L);
        when(courseRepository.findByCourseCode("MATH-2")).thenReturn(Optional.empty());

        CourseApi.CourseResponse updated = service.update(7L, new CourseApi.CourseRequest("MATH-2", "Advanced Math", "Updated", "ACTIVE"));
        service.disable(7L);

        assertThat(created.id()).isEqualTo(7L);
        assertThat(updated.courseCode()).isEqualTo("MATH-2");
        assertThat(course.getCourseName()).isEqualTo("Advanced Math");
        assertThat(course.getStatus()).isEqualTo("DISABLED");
    }

    private Course course(String code, String name, String description, String status) {
        return new Course(code, name, description, status);
    }
}
