package com.courseservice.services;

import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.events.producers.CourseEventProducer;
import com.courseservice.models.*;
import com.courseservice.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleProgressCheckerTest {

    @Mock LessonRepository lessonRepository;
    @Mock LessonProgressRepository lessonProgressRepository;
    @Mock QuizRepository quizRepository;
    @Mock QuizAttemptRepository quizAttemptRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock CourseEventProducer courseEventProducer;

    @InjectMocks ModuleProgressChecker checker;

    private static final UUID USER_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MODULE1_ID   = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MODULE2_ID   = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ENROLMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void tryComplete_lessonsNotDone_returnsNotDone() {
        com.courseservice.models.Module module = buildModule(MODULE1_ID, buildCourse(), 1);
        Enrolment enrolment = buildEnrolment();

        when(lessonRepository.countByModuleId(MODULE1_ID)).thenReturn(2L);
        when(lessonProgressRepository.countByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(1L);

        ModuleProgressChecker.ModuleProgressResult result = checker.tryComplete(module, enrolment);

        assertThat(result.nextModuleId()).isNull();
        assertThat(result.courseCompleted()).isFalse();
        verify(moduleUnlockRepository, never()).save(any());
        verify(enrolmentRepository, never()).save(any());
    }

    @Test
    void tryComplete_quizzesNotPassed_returnsNotDone() {
        com.courseservice.models.Module module = buildModule(MODULE1_ID, buildCourse(), 1);
        Enrolment enrolment = buildEnrolment();

        when(lessonRepository.countByModuleId(MODULE1_ID)).thenReturn(1L);
        when(lessonProgressRepository.countByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(1L);
        when(quizRepository.countByModuleId(MODULE1_ID)).thenReturn(1L);
        when(quizAttemptRepository.countPassedDistinctByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(0L);

        ModuleProgressChecker.ModuleProgressResult result = checker.tryComplete(module, enrolment);

        assertThat(result.nextModuleId()).isNull();
        assertThat(result.courseCompleted()).isFalse();
        verify(moduleUnlockRepository, never()).save(any());
    }

    @Test
    void tryComplete_allDone_nextModuleExists_unlocksNextModule() {
        Course course = buildCourse();
        com.courseservice.models.Module module1 = buildModule(MODULE1_ID, course, 1);
        com.courseservice.models.Module module2 = buildModule(MODULE2_ID, course, 2);
        module2.setTitle("Module 2");
        Enrolment enrolment = buildEnrolment();

        when(lessonRepository.countByModuleId(MODULE1_ID)).thenReturn(1L);
        when(lessonProgressRepository.countByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(1L);
        when(quizRepository.countByModuleId(MODULE1_ID)).thenReturn(1L);
        when(quizAttemptRepository.countPassedDistinctByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(1L);
        when(moduleRepository.findByCourseIdOrderByOrderIndex(COURSE_ID)).thenReturn(List.of(module1, module2));
        when(moduleUnlockRepository.save(any(ModuleUnlock.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleProgressChecker.ModuleProgressResult result = checker.tryComplete(module1, enrolment);

        assertThat(result.nextModuleId()).isEqualTo(MODULE2_ID);
        assertThat(result.courseCompleted()).isFalse();

        ArgumentCaptor<ModuleUnlock> unlockCaptor = ArgumentCaptor.forClass(ModuleUnlock.class);
        verify(moduleUnlockRepository).save(unlockCaptor.capture());
        assertThat(unlockCaptor.getValue().getModule().getId()).isEqualTo(MODULE2_ID);
        verify(courseEventProducer).emitModuleUnlocked(any());
        verify(enrolmentRepository, never()).save(any());
    }

    @Test
    void tryComplete_allDone_lastModule_completesCourse() {
        Course course = buildCourse();
        com.courseservice.models.Module module = buildModule(MODULE1_ID, course, 1);
        Enrolment enrolment = buildEnrolment();

        when(lessonRepository.countByModuleId(MODULE1_ID)).thenReturn(1L);
        when(lessonProgressRepository.countByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(1L);
        when(quizRepository.countByModuleId(MODULE1_ID)).thenReturn(0L);
        when(quizAttemptRepository.countPassedDistinctByUserIdAndModuleId(USER_ID, MODULE1_ID)).thenReturn(0L);
        when(moduleRepository.findByCourseIdOrderByOrderIndex(COURSE_ID)).thenReturn(List.of(module));
        when(enrolmentRepository.save(any(Enrolment.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleProgressChecker.ModuleProgressResult result = checker.tryComplete(module, enrolment);

        assertThat(result.courseCompleted()).isTrue();
        assertThat(result.nextModuleId()).isNull();

        ArgumentCaptor<Enrolment> captor = ArgumentCaptor.forClass(Enrolment.class);
        verify(enrolmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EnrolmentStatus.COMPLETED);
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
        verify(courseEventProducer).emitCourseCompleted(any());
        verify(moduleUnlockRepository, never()).save(any());
    }

    // --- helpers ---

    private Course buildCourse() {
        Course c = new Course();
        c.setId(COURSE_ID);
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

    private Enrolment buildEnrolment() {
        Enrolment e = new Enrolment();
        e.setId(ENROLMENT_ID);
        e.setUserId(USER_ID);
        Course course = new Course();
        course.setId(COURSE_ID);
        e.setCourse(course);
        return e;
    }
}
