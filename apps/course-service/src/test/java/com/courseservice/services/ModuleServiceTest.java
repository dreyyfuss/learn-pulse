package com.courseservice.services;

import com.courseservice.dto.request.CreateModuleRequest;
import com.courseservice.dto.request.UpdateModuleRequest;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Module;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock CourseService courseService;
    @Mock ModuleRepository moduleRepository;

    @InjectMocks ModuleService moduleService;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MODULE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_notOwner_throws() {
        when(courseService.loadAndGuard(COURSE_ID, OTHER_ID))
                .thenThrow(new NotOwnerException("You do not own this course."));

        assertThatThrownBy(() -> moduleService.create(COURSE_ID,
                new CreateModuleRequest("Intro", null, 0), OTHER_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void create_lockedCourse_throws() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID))
                .thenThrow(new CourseAlreadyStartedException("Course is locked."));

        assertThatThrownBy(() -> moduleService.create(COURSE_ID,
                new CreateModuleRequest("Intro", null, 0), INSTRUCTOR_ID))
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

        assertThatThrownBy(() -> moduleService.update(COURSE_ID, MODULE_ID,
                new UpdateModuleRequest(null, null, null), INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_success() {
        Course course = new Course();
        course.setId(COURSE_ID);

        Module saved = new Module();
        saved.setId(MODULE_ID);
        saved.setCourse(course);
        saved.setTitle("Week 1");
        saved.setOrderIndex(0);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.save(any(Module.class))).thenReturn(saved);

        var response = moduleService.create(COURSE_ID,
                new CreateModuleRequest("Week 1", null, 0), INSTRUCTOR_ID);

        assertThat(response.id()).isEqualTo(MODULE_ID);
        assertThat(response.title()).isEqualTo("Week 1");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_notOwner_throws() {
        when(courseService.loadAndGuard(COURSE_ID, OTHER_ID))
                .thenThrow(new NotOwnerException("You do not own this course."));

        assertThatThrownBy(() -> moduleService.update(COURSE_ID, MODULE_ID,
                new UpdateModuleRequest("New Title", null, null), OTHER_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void update_lockedCourse_throws() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID))
                .thenThrow(new CourseAlreadyStartedException("Course is locked."));

        assertThatThrownBy(() -> moduleService.update(COURSE_ID, MODULE_ID,
                new UpdateModuleRequest("New Title", null, null), INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }

    @Test
    void update_moduleNotFound_throws() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(new Course());
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moduleService.update(COURSE_ID, MODULE_ID,
                new UpdateModuleRequest("New Title", null, null), INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_moduleNotInCourse_throws() {
        Course otherCourse = new Course();
        otherCourse.setId(UUID.randomUUID());

        Module module = new Module();
        module.setId(MODULE_ID);
        module.setCourse(otherCourse);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(new Course());
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));

        assertThatThrownBy(() -> moduleService.update(COURSE_ID, MODULE_ID,
                new UpdateModuleRequest("New Title", null, null), INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_success_updatesNonNullFields() {
        Course course = new Course();
        course.setId(COURSE_ID);

        Module existing = new Module();
        existing.setId(MODULE_ID);
        existing.setCourse(course);
        existing.setTitle("Old Title");
        existing.setDescription("Old Description");
        existing.setOrderIndex(0);

        Module saved = new Module();
        saved.setId(MODULE_ID);
        saved.setCourse(course);
        saved.setTitle("New Title");
        saved.setDescription("Old Description");
        saved.setOrderIndex(0);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
        when(moduleRepository.save(any(Module.class))).thenReturn(saved);

        var response = moduleService.update(COURSE_ID, MODULE_ID,
                new UpdateModuleRequest("New Title", null, null), INSTRUCTOR_ID);

        assertThat(response.id()).isEqualTo(MODULE_ID);
        assertThat(response.title()).isEqualTo("New Title");
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_notOwner_throws() {
        when(courseService.loadAndGuard(COURSE_ID, OTHER_ID))
                .thenThrow(new NotOwnerException("You do not own this course."));

        assertThatThrownBy(() -> moduleService.delete(COURSE_ID, MODULE_ID, OTHER_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void delete_lockedCourse_throws() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID))
                .thenThrow(new CourseAlreadyStartedException("Course is locked."));

        assertThatThrownBy(() -> moduleService.delete(COURSE_ID, MODULE_ID, INSTRUCTOR_ID))
                .isInstanceOf(CourseAlreadyStartedException.class);
    }

    @Test
    void delete_moduleNotFound_throws() {
        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(new Course());
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moduleService.delete(COURSE_ID, MODULE_ID, INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_moduleNotInCourse_throws() {
        Course otherCourse = new Course();
        otherCourse.setId(UUID.randomUUID());

        Module module = new Module();
        module.setId(MODULE_ID);
        module.setCourse(otherCourse);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(new Course());
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));

        assertThatThrownBy(() -> moduleService.delete(COURSE_ID, MODULE_ID, INSTRUCTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_success_invokesRepositoryDelete() {
        Course course = new Course();
        course.setId(COURSE_ID);

        Module module = new Module();
        module.setId(MODULE_ID);
        module.setCourse(course);

        when(courseService.loadAndGuard(COURSE_ID, INSTRUCTOR_ID)).thenReturn(course);
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));

        moduleService.delete(COURSE_ID, MODULE_ID, INSTRUCTOR_ID);

        verify(moduleRepository).delete(module);
    }
}
