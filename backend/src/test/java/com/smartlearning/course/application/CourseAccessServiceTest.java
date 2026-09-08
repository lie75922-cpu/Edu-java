package com.smartlearning.course.application;

import com.smartlearning.auth.domain.CurrentUser;
import com.smartlearning.common.exception.ForbiddenOperationException;
import com.smartlearning.course.domain.Course;
import com.smartlearning.course.domain.TeacherAssignmentStatus;
import com.smartlearning.course.infrastructure.persistence.CourseEnrollmentRepository;
import com.smartlearning.course.infrastructure.persistence.CourseRepository;
import com.smartlearning.course.infrastructure.persistence.CourseTeacherAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAccessServiceTest {

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private CourseTeacherAssignmentRepository assignmentRepository;
    @Mock
    private CourseRepository courseRepository;

    @Test
    void teacherRequiresBothAnActiveCourseAndAnActiveAssignment() {
        CourseAccessService service = service();
        CurrentUser teacher = new CurrentUser(11L, "teacher", Set.of("TEACHER"));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course(7L, "ACTIVE")));
        when(assignmentRepository.existsByTeacherIdAndCourseIdAndStatus(11L, 7L, TeacherAssignmentStatus.ACTIVE))
                .thenReturn(true);

        assertThatCode(() -> service.requireTeachingAccess(7L, teacher)).doesNotThrowAnyException();

        when(assignmentRepository.existsByTeacherIdAndCourseIdAndStatus(11L, 7L, TeacherAssignmentStatus.ACTIVE))
                .thenReturn(false);
        assertThatThrownBy(() -> service.requireTeachingAccess(7L, teacher))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not assigned");
    }

    @Test
    void studentStillNeedsAnActiveEnrollmentAndPlatformTeachingAdminsBypassCourseAssignments() {
        CourseAccessService service = service();
        CurrentUser student = new CurrentUser(12L, "student", Set.of("STUDENT"));
        when(courseRepository.findById(7L)).thenReturn(Optional.of(course(7L, "ACTIVE")));
        when(enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(12L, 7L, "ACTIVE")).thenReturn(false);

        assertThatThrownBy(() -> service.requireCourseReadAccess(7L, student))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not enrolled");

        when(courseRepository.findById(7L)).thenReturn(Optional.of(course(7L, "DISABLED")));
        assertThatThrownBy(() -> service.requireCourseReadAccess(7L, student))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("active course");

        CurrentUser teachingAdmin = new CurrentUser(13L, "teach-admin", Set.of("TEACH_ADMIN"));
        assertThatCode(() -> service.requireTeachingAccess(7L, teachingAdmin)).doesNotThrowAnyException();
    }

    private CourseAccessService service() {
        return new CourseAccessService(enrollmentRepository, assignmentRepository, courseRepository);
    }

    private Course course(long id, String status) {
        Course course = new Course("COURSE-" + id, "Course", null, status);
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }
}
