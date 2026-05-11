package com.courseservice.service;

import com.courseservice.dto.request.CreateCourseRequest;
import com.courseservice.dto.request.UpdateCourseRequest;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.dto.response.CourseResponse;
import com.courseservice.dto.response.CreateCourseResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.exception.CourseNotPublishableException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.services.CourseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final UUID OTHER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Test
    void get_found_returnsCourseResponse() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);
        course.setTitle("Intro to Java");
        course.setVisibility(CourseVisibility.PUBLIC);

        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));

        CourseResponse response = courseService.get(COURSE_ID);

        assertThat(response.id()).isEqualTo(COURSE_ID);
        assertThat(response.title()).isEqualTo("Intro to Java");
    }

    @Test
    void get_notFound_throwsResourceNotFoundException() {
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.get(COURSE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // listOwn
    // -------------------------------------------------------------------------

    @Test
    void listOwn_delegatesToRepository() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(courseRepository.findAllByInstructorId(INSTRUCTOR_ID, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<CourseSummaryResponse> response = courseService.listOwn(INSTRUCTOR_ID, pageable);

        assertThat(response.items()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    @Test
    void list_delegatesToFindPublishedCourses() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(courseRepository.findPublishedCourses(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<CourseSummaryResponse> response = courseService.list(null, null, null, pageable);

        assertThat(response.items()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_courseNotFound_throws() {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.update(COURSE_ID,
                new UpdateCourseRequest("New Title", null, null, null, null), INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_notOwner_throws() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(OTHER_ID);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.update(COURSE_ID,
                new UpdateCourseRequest("New Title", null, null, null, null), INSTRUCTOR_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void update_lockedCourse_throws() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);
        course.setLocked(true);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.update(COURSE_ID,
                new UpdateCourseRequest("New Title", null, null, null, null), INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }

    @Test
    void update_partialFields_onlyUpdatesNonNull() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);
        course.setTitle("Old Title");
        course.setDescription("Original description");
        course.setVisibility(CourseVisibility.PUBLIC);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);

        courseService.update(COURSE_ID,
                new UpdateCourseRequest("New Title", null, null, null, null), INSTRUCTOR_ID);

        verify(courseRepository).save(captor.capture());
        Course saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("New Title");
        assertThat(saved.getDescription()).isEqualTo("Original description");
    }

    @Test
    void update_switchToPrivate_generatesEnrolmentCode() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);
        course.setVisibility(CourseVisibility.PUBLIC);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);

        courseService.update(COURSE_ID,
                new UpdateCourseRequest(null, null, null, null, CourseVisibility.PRIVATE), INSTRUCTOR_ID);

        verify(courseRepository).save(captor.capture());
        Course saved = captor.getValue();
        assertThat(saved.getEnrolmentCode()).isNotNull();
        assertThat(saved.getEnrolmentCode()).hasSize(8);
        assertThat(saved.getVisibility()).isEqualTo(CourseVisibility.PRIVATE);
    }

    // -------------------------------------------------------------------------
    // publish
    // -------------------------------------------------------------------------

    @Test
    void publish_courseNotFound_throws() {
        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.publish(COURSE_ID, INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publish_notOwner_throws() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(OTHER_ID);

        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.publish(COURSE_ID, INSTRUCTOR_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void publish_noModules_throwsCourseNotPublishableException() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);

        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.publish(COURSE_ID, INSTRUCTOR_ID))
                .isInstanceOf(CourseNotPublishableException.class);
    }

    @Test
    void publish_moduleWithNoLessons_throwsCourseNotPublishableException() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);

        Module module = new Module();
        module.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        module.setCourse(course);
        course.getModules().add(module);

        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.publish(COURSE_ID, INSTRUCTOR_ID))
                .isInstanceOf(CourseNotPublishableException.class);
    }

    @Test
    void publish_success_setsPublishedStatusAndTimestamp() {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setInstructorId(INSTRUCTOR_ID);
        course.setTitle("Publishable Course");
        course.setVisibility(CourseVisibility.PUBLIC);

        Module module = new Module();
        module.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        module.setCourse(course);

        Lesson lesson = new Lesson();
        lesson.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        lesson.setModule(module);
        module.getLessons().add(lesson);

        course.getModules().add(module);

        when(courseRepository.findWithModulesAndLessonsById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseSummaryResponse response = courseService.publish(COURSE_ID, INSTRUCTOR_ID);

        assertThat(response.status()).isEqualTo(CourseStatus.PUBLISHED);
        assertThat(response.publishedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_courseNotFound_throws() {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.delete(COURSE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_success_invokesRepositoryDelete() {
        Course course = new Course();
        course.setId(COURSE_ID);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

        courseService.delete(COURSE_ID);

        verify(courseRepository).delete(course);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void stubSave(UUID assignedId) {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(assignedId);
            return c;
        });
    }
}
