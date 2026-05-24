package com.courseservice.services;

import com.courseservice.dto.response.LessonCompleteResponse;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.exception.LessonOutOfOrderException;
import com.courseservice.exception.ModuleLockedForUserException;
import com.courseservice.models.*;
import com.courseservice.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonProgressServiceTest {

    @Mock LessonRepository lessonRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock LessonProgressRepository lessonProgressRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock ModuleProgressChecker moduleProgressChecker;
    @Mock StreakService streakService;

    @InjectMocks LessonProgressService lessonProgressService;

    private static final UUID USER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MODULE1_ID   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MODULE2_ID   = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID LESSON_ID    = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID LESSON2_ID   = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ENROLMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void complete_happyPath_savesProgressAndReturnsUnlockedModule() {
        Course course = buildCourse();
        com.courseservice.models.Module module1 = buildModule(MODULE1_ID, course, 1);
        Lesson lesson = buildLesson(LESSON_ID, module1, 1);
        Enrolment enrolment = buildEnrolment(COURSE_ID);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE1_ID))
                .thenReturn(Optional.of(new ModuleUnlock()));
        when(lessonRepository.findByModuleIdOrderByOrderIndex(MODULE1_ID)).thenReturn(List.of(lesson));
        when(lessonProgressRepository.findByUserIdAndLessonId(USER_ID, LESSON_ID)).thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(moduleProgressChecker.tryComplete(any(), any()))
                .thenReturn(new ModuleProgressChecker.ModuleProgressResult(MODULE2_ID, false));

        LessonCompleteResponse response = lessonProgressService.complete(LESSON_ID, USER_ID);

        assertThat(response.lessonId()).isEqualTo(LESSON_ID);
        assertThat(response.nextModuleId()).isEqualTo(MODULE2_ID);
        assertThat(response.courseCompleted()).isFalse();
        verify(lessonProgressRepository, times(1)).save(any(LessonProgress.class));
        verify(streakService).recordActivity(USER_ID);
    }

    @Test
    void complete_lastLessonInFinalModule_completesCourse() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE1_ID, course, 1);
        Lesson lesson = buildLesson(LESSON_ID, module, 1);
        Enrolment enrolment = buildEnrolment(COURSE_ID);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE1_ID))
                .thenReturn(Optional.of(new ModuleUnlock()));
        when(lessonRepository.findByModuleIdOrderByOrderIndex(MODULE1_ID)).thenReturn(List.of(lesson));
        when(lessonProgressRepository.findByUserIdAndLessonId(USER_ID, LESSON_ID)).thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(moduleProgressChecker.tryComplete(any(), any()))
                .thenReturn(new ModuleProgressChecker.ModuleProgressResult(null, true));

        LessonCompleteResponse response = lessonProgressService.complete(LESSON_ID, USER_ID);

        assertThat(response.courseCompleted()).isTrue();
        assertThat(response.nextModuleId()).isNull();
        verify(moduleProgressChecker).tryComplete(any(), any());
    }

    @Test
    void complete_moduleLocked_throwsModuleLocked() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE1_ID, course, 1);
        Lesson lesson = buildLesson(LESSON_ID, module, 1);
        Enrolment enrolment = buildEnrolment(COURSE_ID);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE1_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> lessonProgressService.complete(LESSON_ID, USER_ID))
                .isInstanceOf(ModuleLockedForUserException.class);
    }

    @Test
    void complete_prerequisiteNotDone_throwsLessonOutOfOrder() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE1_ID, course, 1);
        Lesson lesson1 = buildLesson(LESSON2_ID, module, 1);
        Lesson lesson2 = buildLesson(LESSON_ID, module, 2);
        Enrolment enrolment = buildEnrolment(COURSE_ID);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson2));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE1_ID))
                .thenReturn(Optional.of(new ModuleUnlock()));
        when(lessonRepository.findByModuleIdOrderByOrderIndex(MODULE1_ID))
                .thenReturn(List.of(lesson1, lesson2));
        when(lessonProgressRepository.findByUserIdAndLessonIdIn(eq(USER_ID), any()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> lessonProgressService.complete(LESSON_ID, USER_ID))
                .isInstanceOf(LessonOutOfOrderException.class);
    }

    @Test
    void complete_alreadyCompleted_returnsIdempotentResponse() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE1_ID, course, 1);
        Lesson lesson = buildLesson(LESSON_ID, module, 1);
        Enrolment enrolment = buildEnrolment(COURSE_ID);

        LessonProgress existing = new LessonProgress();
        existing.setId(UUID.randomUUID());
        existing.setUserId(USER_ID);
        existing.setLesson(lesson);

        when(lessonRepository.findById(LESSON_ID)).thenReturn(Optional.of(lesson));
        when(enrolmentRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.findByEnrolmentIdAndModuleId(ENROLMENT_ID, MODULE1_ID))
                .thenReturn(Optional.of(new ModuleUnlock()));
        when(lessonRepository.findByModuleIdOrderByOrderIndex(MODULE1_ID)).thenReturn(List.of(lesson));
        when(lessonProgressRepository.findByUserIdAndLessonId(USER_ID, LESSON_ID))
                .thenReturn(Optional.of(existing));
        when(moduleProgressChecker.tryComplete(any(), any()))
                .thenReturn(new ModuleProgressChecker.ModuleProgressResult(null, false));

        LessonCompleteResponse response = lessonProgressService.complete(LESSON_ID, USER_ID);

        assertThat(response.lessonId()).isEqualTo(LESSON_ID);
        verify(lessonProgressRepository, never()).save(any());
        verify(streakService, never()).recordActivity(any());
    }

    // --- helpers ---

    private Course buildCourse() {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setVisibility(CourseVisibility.PUBLIC);
        return c;
    }

    private com.courseservice.models.Module buildModule(UUID id, Course course, int orderIndex) {
        com.courseservice.models.Module m = new com.courseservice.models.Module();
        m.setId(id);
        m.setCourse(course);
        m.setTitle("Module " + orderIndex);
        m.setOrderIndex(orderIndex);
        return m;
    }

    private Lesson buildLesson(UUID id, com.courseservice.models.Module module, int orderIndex) {
        Lesson l = new Lesson();
        l.setId(id);
        l.setModule(module);
        l.setTitle("Lesson " + orderIndex);
        l.setOrderIndex(orderIndex);
        return l;
    }

    private Enrolment buildEnrolment(UUID courseId) {
        Enrolment e = new Enrolment();
        e.setId(ENROLMENT_ID);
        e.setUserId(USER_ID);
        e.setStartedAt(LocalDateTime.now().minusDays(1));
        Course course = new Course();
        course.setId(courseId);
        e.setCourse(course);
        return e;
    }
}
