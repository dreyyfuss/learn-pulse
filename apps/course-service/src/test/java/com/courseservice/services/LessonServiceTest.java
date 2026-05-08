package com.courseservice.services;

import com.courseservice.dto.request.CreateLessonRequest;
import com.courseservice.enums.ContentType;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock CourseService courseService;
    @Mock ModuleRepository moduleRepository;
    @Mock LessonRepository lessonRepository;

    @InjectMocks LessonService lessonService;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MODULE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID LESSON_ID     = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void create_outsideOwnedCourse_throwsNotOwnerException() {
        when(courseService.loadAndGuard(COURSE_ID, OTHER_ID))
                .thenThrow(new NotOwnerException("You do not own this course."));

        assertThatThrownBy(() -> lessonService.create(COURSE_ID, MODULE_ID,
                new CreateLessonRequest("Lesson", null, ContentType.VIDEO, null, 0, null), OTHER_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void create_lockedCourse_throws() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID))
                .thenThrow(new CourseAlreadyStartedException("Course is locked."));

        assertThatThrownBy(() -> lessonService.create(COURSE_ID, MODULE_ID,
                new CreateLessonRequest("Lesson", null, ContentType.VIDEO, null, 0, null), INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }

    @Test
    void create_moduleNotInCourse_throws() {
        Course otherCourse = new Course();
        otherCourse.setId(UUID.randomUUID());

        Module module = new Module();
        module.setId(MODULE_ID);
        module.setCourse(otherCourse);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(new Course());
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));

        assertThatThrownBy(() -> lessonService.create(COURSE_ID, MODULE_ID,
                new CreateLessonRequest("Lesson", null, ContentType.VIDEO, null, 0, null), INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_success() {
        Course course = new Course();
        course.setId(COURSE_ID);

        Module module = new Module();
        module.setId(MODULE_ID);
        module.setCourse(course);

        Lesson saved = new Lesson();
        saved.setId(LESSON_ID);
        saved.setModule(module);
        saved.setTitle("Intro Video");
        saved.setContentType(ContentType.VIDEO);
        saved.setOrderIndex(0);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
        when(lessonRepository.save(any(Lesson.class))).thenReturn(saved);

        var response = lessonService.create(COURSE_ID, MODULE_ID,
                new CreateLessonRequest("Intro Video", null, ContentType.VIDEO, null, 0, null), INSTRUCTOR_ID);

        assertThat(response.id()).isEqualTo(LESSON_ID);
        assertThat(response.contentType()).isEqualTo(ContentType.VIDEO);
    }
}
