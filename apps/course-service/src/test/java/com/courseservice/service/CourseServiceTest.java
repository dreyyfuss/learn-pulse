package com.courseservice.service;

import com.courseservice.dto.request.CreateCourseRequest;
import com.courseservice.dto.response.CreateCourseResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.services.CourseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    CourseRepository courseRepository;

    @InjectMocks
    CourseService courseService;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void create_publicCourse_enrolmentCodeIsNull() {
        stubSave(COURSE_ID);

        CreateCourseResponse response = courseService.create(
                new CreateCourseRequest("Intro to Java", null, null, null, CourseVisibility.PUBLIC),
                INSTRUCTOR_ID);

        assertThat(response.enrolmentCode()).isNull();
    }

    @Test
    void create_privateCourse_enrolmentCodePopulated() {
        stubSave(COURSE_ID);

        CreateCourseResponse response = courseService.create(
                new CreateCourseRequest("Private Java", null, null, null, CourseVisibility.PRIVATE),
                INSTRUCTOR_ID);

        assertThat(response.enrolmentCode()).isNotNull();
        assertThat(response.enrolmentCode()).hasSize(8);
        assertThat(response.enrolmentCode()).matches("[A-Z0-9]+");
    }

    @Test
    void create_setsInstructorIdAndDraftStatus() {
        stubSave(COURSE_ID);
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);

        courseService.create(
                new CreateCourseRequest("My Course", "A description", null, "Tech", CourseVisibility.PUBLIC),
                INSTRUCTOR_ID);

        verify(courseRepository).save(captor.capture());
        Course saved = captor.getValue();
        assertThat(saved.getInstructorId()).isEqualTo(INSTRUCTOR_ID);
        assertThat(saved.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(saved.isLocked()).isFalse();
        assertThat(saved.getTitle()).isEqualTo("My Course");
        assertThat(saved.getCategory()).isEqualTo("Tech");
    }

    @Test
    void create_returnsAssignedCourseId() {
        stubSave(COURSE_ID);

        CreateCourseResponse response = courseService.create(
                new CreateCourseRequest("Course X", null, null, null, CourseVisibility.PUBLIC),
                INSTRUCTOR_ID);

        assertThat(response.courseId()).isEqualTo(COURSE_ID);
    }

    private void stubSave(UUID assignedId) {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(assignedId);
            return c;
        });
    }
}
