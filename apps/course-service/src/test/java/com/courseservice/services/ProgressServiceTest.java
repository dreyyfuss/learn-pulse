package com.courseservice.services;

import com.courseservice.dto.response.CompleteLessonResponse;
import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.exception.LessonOutOfOrderException;
import com.courseservice.exception.ModuleLockedException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import com.courseservice.services.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock LessonRepository lessonRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock CourseRepository courseRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock LessonProgressRepository lessonProgressRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock UserRepository userRepository;
    @Mock OutboxService outboxService;

    @InjectMocks ProgressService progressService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    User learner;
    User instructor;
    Course course;
    Module module1, module2;
    Lesson lesson1, lesson2, lesson3; // lesson1+2 in module1; lesson3 only lesson in module2
    Enrolment enrolment;

    @BeforeEach
    void setUp() {
        learner    = User.builder().id(1L).roles(new HashSet<>(Set.of(Role.LEARNER))).build();
        instructor = User.builder().id(99L).roles(new HashSet<>(Set.of(Role.INSTRUCTOR))).build();

        course  = Course.builder().id(10L).instructorId(99L).title("Java 101")
                         .status(CourseStatus.PUBLISHED).visibility(CourseVisibility.PUBLIC).build();

        module1 = Module.builder().id(1L).course(course).title("Module 1").orderIndex(0).build();
        module2 = Module.builder().id(2L).course(course).title("Module 2").orderIndex(1).build();

        lesson1 = Lesson.builder().id(1L).module(module1).title("Lesson 1")
                         .orderIndex(0).contentType(ContentType.VIDEO).build();
        lesson2 = Lesson.builder().id(2L).module(module1).title("Lesson 2")
                         .orderIndex(1).contentType(ContentType.VIDEO).build();
        lesson3 = Lesson.builder().id(3L).module(module2).title("Lesson 3")
                         .orderIndex(0).contentType(ContentType.ARTICLE).build();

        enrolment = Enrolment.builder().id(100L).user(learner).course(course)
                              .status(EnrolmentStatus.ACTIVE).build();
    }

    // ── getProgress ──────────────────────────────────────────────────────────

    @Test
    void getProgress_notStarted_allModulesLockedAndNullCurrentLesson() {
        stubGetProgress(List.of(), List.of());

        ProgressResponse result = progressService.getProgress(100L, learner);

        assertThat(result.enrolmentId()).isEqualTo(100L);
        assertThat(result.courseId()).isEqualTo(10L);
        assertThat(result.courseTitle()).isEqualTo("Java 101");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.currentLessonId()).isNull();
        assertThat(result.modules()).hasSize(2);
        assertThat(result.modules()).allMatch(m -> !m.unlocked());
        assertThat(result.modules().get(0).lessons()).allMatch(l -> !l.completed());
    }

    @Test
    void getProgress_lesson1Done_currentIsLesson2AndModule1Unlocked() {
        enrolment.setStartedAt(LocalDateTime.now().minusHours(1));

        ModuleUnlock u1 = unlock(module1);
        LessonProgress lp1 = progress(lesson1);

        stubGetProgress(List.of(u1), List.of(lp1));

        ProgressResponse result = progressService.getProgress(100L, learner);

        ProgressResponse.ModuleProgress m1 = result.modules().get(0);
        ProgressResponse.ModuleProgress m2 = result.modules().get(1);

        assertThat(result.currentLessonId()).isEqualTo(2L); // lesson2 is next
        assertThat(m1.unlocked()).isTrue();
        assertThat(m2.unlocked()).isFalse();
        assertThat(m1.lessons().get(0).completed()).isTrue();  // lesson1
        assertThat(m1.lessons().get(1).completed()).isFalse(); // lesson2
    }

    @Test
    void getProgress_module1Complete_module2UnlockedAndCurrentIsLesson3() {
        enrolment.setStartedAt(LocalDateTime.now().minusHours(2));

        ModuleUnlock u1 = unlock(module1);
        ModuleUnlock u2 = unlock(module2);
        LessonProgress lp1 = progress(lesson1);
        LessonProgress lp2 = progress(lesson2);

        stubGetProgress(List.of(u1, u2), List.of(lp1, lp2));

        ProgressResponse result = progressService.getProgress(100L, learner);

        assertThat(result.currentLessonId()).isEqualTo(3L);
        assertThat(result.modules().get(0).unlocked()).isTrue();
        assertThat(result.modules().get(1).unlocked()).isTrue();
        assertThat(result.modules().get(0).lessons()).allMatch(ProgressResponse.LessonItem::completed);
        assertThat(result.modules().get(1).lessons().get(0).completed()).isFalse();
    }

    @Test
    void getProgress_courseComplete_noCurrentLessonAndStatusCompleted() {
        enrolment.setStartedAt(LocalDateTime.now().minusDays(1));
        enrolment.setStatus(EnrolmentStatus.COMPLETED);
        enrolment.setCompletedAt(LocalDateTime.now());

        stubGetProgress(List.of(unlock(module1), unlock(module2)),
                        List.of(progress(lesson1), progress(lesson2), progress(lesson3)));

        ProgressResponse result = progressService.getProgress(100L, learner);

        assertThat(result.currentLessonId()).isNull();
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.modules()).allMatch(ProgressResponse.ModuleProgress::unlocked);
        result.modules().forEach(m ->
                assertThat(m.lessons()).allMatch(ProgressResponse.LessonItem::completed));
    }

    @Test
    void getProgress_callerIsInstructorOwner_allowed() {
        stubGetProgress(List.of(), List.of());

        assertThatCode(() -> progressService.getProgress(100L, instructor)).doesNotThrowAnyException();
    }

    @Test
    void getProgress_callerIsAdmin_allowed() {
        User admin = User.builder().id(55L).roles(new HashSet<>(Set.of(Role.ADMIN))).build();
        stubGetProgress(List.of(), List.of());

        assertThatCode(() -> progressService.getProgress(100L, admin)).doesNotThrowAnyException();
    }

    @Test
    void getProgress_unrelatedLearner_throwsAccessDenied() {
        User stranger = User.builder().id(2L).roles(new HashSet<>(Set.of(Role.LEARNER))).build();

        when(enrolmentRepository.findById(100L)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> progressService.getProgress(100L, stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getProgress_enrolmentNotFound_throwsResourceNotFound() {
        when(enrolmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.getProgress(999L, learner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── completeLesson ───────────────────────────────────────────────────────

    @Test
    void completeLesson_firstLessonInModule_noSideEffects() {
        enrolment.setStartedAt(LocalDateTime.now());
        LessonProgress saved = savedProgress(lesson1);

        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson1));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(100L, 1L)).thenReturn(true);
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(lesson1, lesson2));
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 1L)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(learner);
        when(lessonProgressRepository.save(any())).thenReturn(saved);

        CompleteLessonResponse result = progressService.completeLesson(1L, 1L);

        assertThat(result.lessonId()).isEqualTo(1L);
        assertThat(result.unlockedModuleId()).isNull();
        assertThat(result.courseCompleted()).isNull();
        verify(moduleUnlockRepository, never()).save(any());
        verify(enrolmentRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void completeLesson_lastLessonOfNonFinalModule_unlocksNextModule() {
        enrolment.setStartedAt(LocalDateTime.now());
        LessonProgress saved = savedProgress(lesson2);

        when(lessonRepository.findById(2L)).thenReturn(Optional.of(lesson2));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(100L, 1L)).thenReturn(true);
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(lesson1, lesson2));
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 2L)).thenReturn(false);
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 1L)).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(learner);
        when(lessonProgressRepository.save(any())).thenReturn(saved);
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(10L)).thenReturn(List.of(module1, module2));

        CompleteLessonResponse result = progressService.completeLesson(2L, 1L);

        assertThat(result.unlockedModuleId()).isEqualTo(2L);
        assertThat(result.courseCompleted()).isNull();

        ArgumentCaptor<ModuleUnlock> cap = ArgumentCaptor.forClass(ModuleUnlock.class);
        verify(moduleUnlockRepository).save(cap.capture());
        assertThat(cap.getValue().getModule().getId()).isEqualTo(2L);
        verify(enrolmentRepository, never()).save(any());

        // module.unlocked event must be published so the email consumer can send a notification
        verify(outboxService).publish(eq("module.unlocked"), eq("1"), any());
    }

    @Test
    void completeLesson_lastLessonOfFinalModule_marksEnrolmentCompleted() {
        enrolment.setStartedAt(LocalDateTime.now());
        LessonProgress saved = savedProgress(lesson3);

        when(lessonRepository.findById(3L)).thenReturn(Optional.of(lesson3));
        when(moduleRepository.findById(2L)).thenReturn(Optional.of(module2));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(100L, 2L)).thenReturn(true);
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(2L)).thenReturn(List.of(lesson3));
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 3L)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(learner);
        when(lessonProgressRepository.save(any())).thenReturn(saved);
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(10L)).thenReturn(List.of(module1, module2));

        CompleteLessonResponse result = progressService.completeLesson(3L, 1L);

        assertThat(result.courseCompleted()).isTrue();
        assertThat(result.unlockedModuleId()).isNull();

        ArgumentCaptor<Enrolment> cap = ArgumentCaptor.forClass(Enrolment.class);
        verify(enrolmentRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(EnrolmentStatus.COMPLETED);
        assertThat(cap.getValue().getCompletedAt()).isEqualTo(saved.getCompletedAt());
        verify(moduleUnlockRepository, never()).save(any());

        // course.completed event must be published; the enrolment ID is the message key
        verify(outboxService).publish(eq("course.completed"), eq("100"), any());
    }

    @Test
    void completeLesson_lessonOutOfOrder_throwsAndNothingSaved() {
        enrolment.setStartedAt(LocalDateTime.now());

        when(lessonRepository.findById(2L)).thenReturn(Optional.of(lesson2));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(100L, 1L)).thenReturn(true);
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(lesson1, lesson2));
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 2L)).thenReturn(false);
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 1L)).thenReturn(false); // lesson1 not done

        assertThatThrownBy(() -> progressService.completeLesson(2L, 1L))
                .isInstanceOf(LessonOutOfOrderException.class)
                .hasMessageContaining("previous lessons");

        verify(lessonProgressRepository, never()).save(any());
        verify(moduleUnlockRepository, never()).save(any());
    }

    @Test
    void completeLesson_moduleLocked_throwsAndNothingSaved() {
        enrolment.setStartedAt(LocalDateTime.now());

        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson1));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(100L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> progressService.completeLesson(1L, 1L))
                .isInstanceOf(ModuleLockedException.class);

        verify(lessonProgressRepository, never()).save(any());
    }

    @Test
    void completeLesson_courseNotStarted_throwsAccessDenied() {
        // enrolment.startedAt is null — learner has not started the course yet
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson1));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));

        assertThatThrownBy(() -> progressService.completeLesson(1L, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void completeLesson_notEnrolled_throwsAccessDenied() {
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson1));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.completeLesson(1L, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void completeLesson_alreadyCompleted_returnsExistingDataWithoutAnyWrites() {
        enrolment.setStartedAt(LocalDateTime.now());
        LocalDateTime originalTime = LocalDateTime.now().minusHours(2);
        LessonProgress existing = LessonProgress.builder().id(5L).user(learner).lesson(lesson1)
                .completedAt(originalTime).build();

        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson1));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module1));
        when(enrolmentRepository.findByUser_IdAndCourse_Id(1L, 10L)).thenReturn(Optional.of(enrolment));
        when(moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(100L, 1L)).thenReturn(true);
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(lesson1, lesson2));
        when(lessonProgressRepository.existsByUser_IdAndLesson_Id(1L, 1L)).thenReturn(true);
        when(lessonProgressRepository.findByUser_IdAndLesson_Id(1L, 1L)).thenReturn(Optional.of(existing));

        CompleteLessonResponse result = progressService.completeLesson(1L, 1L);

        assertThat(result.completedAt()).isEqualTo(originalTime);
        verify(lessonProgressRepository, never()).save(any());
        verify(moduleUnlockRepository, never()).save(any());
        verify(enrolmentRepository, never()).save(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Stubs all repository calls needed by getProgress for the given unlock + progress state. */
    private void stubGetProgress(List<ModuleUnlock> unlocks, List<LessonProgress> progressList) {
        when(enrolmentRepository.findById(100L)).thenReturn(Optional.of(enrolment));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(moduleUnlockRepository.findAllByEnrolment_Id(100L)).thenReturn(unlocks);
        when(lessonProgressRepository.findAllByUser_Id(1L)).thenReturn(progressList);
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(10L))
                .thenReturn(List.of(module1, module2));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(lesson1, lesson2));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(2L))
                .thenReturn(List.of(lesson3));
    }

    private ModuleUnlock unlock(Module module) {
        return ModuleUnlock.builder().id((long) module.getOrderIndex() + 1)
                .enrolment(enrolment).module(module).build();
    }

    private LessonProgress progress(Lesson lesson) {
        return LessonProgress.builder().id(lesson.getId()).user(learner)
                .lesson(lesson).completedAt(LocalDateTime.now().minusMinutes(10)).build();
    }

    private LessonProgress savedProgress(Lesson lesson) {
        return LessonProgress.builder().id(lesson.getId() + 100L).user(learner)
                .lesson(lesson).completedAt(LocalDateTime.now()).build();
    }
}
