package com.courseservice.services;

import com.courseservice.dto.response.CourseResponse;
import com.courseservice.events.dto.CoursePublishedEvent;
import com.courseservice.exception.CoursePublishException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock CourseRepository courseRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock LessonRepository lessonRepository;
    @Mock UserRepository userRepository;
    @Mock OutboxService outboxService;

    @InjectMocks CourseService courseService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    User instructor;
    User otherInstructor;
    Course course;
    Module module1;
    Module module2;
    Lesson lesson1;
    Lesson lesson2;
    Lesson lesson3;

    @BeforeEach
    void setUp() {
        instructor = User.builder().id(10L).fullName("Alice Smith")
                .roles(new HashSet<>(Set.of(Role.INSTRUCTOR))).build();
        otherInstructor = User.builder().id(99L).fullName("Bob Jones")
                .roles(new HashSet<>(Set.of(Role.INSTRUCTOR))).build();

        course = Course.builder().id(1L).instructorId(10L).title("Java 101")
                .status(CourseStatus.DRAFT).visibility(CourseVisibility.PUBLIC).build();

        module1 = Module.builder().id(1L).course(course).title("Intro").description("Intro desc").orderIndex(0).build();
        module2 = Module.builder().id(2L).course(course).title("Advanced").description("Adv desc").orderIndex(1).build();

        lesson1 = Lesson.builder().id(1L).module(module1).title("L1").description("d1")
                .contentType(ContentType.VIDEO).orderIndex(0).build();
        lesson2 = Lesson.builder().id(2L).module(module1).title("L2").description("d2")
                .contentType(ContentType.DOCUMENT).orderIndex(1).build();
        lesson3 = Lesson.builder().id(3L).module(module2).title("L3").description("d3")
                .contentType(ContentType.ARTICLE).orderIndex(0).build();
    }

    // ── Guard: course existence ───────────────────────────────────────────────

    @Test
    void publish_courseNotFound_throwsResourceNotFoundException() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.publish(999L, instructor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Course not found");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    // ── SECURITY: horizontal privilege escalation ─────────────────────────────

    @Test
    void publish_callerIsNotCourseOwner_throwsAccessDeniedException() {
        // Instructor B (id=99) must not be able to publish Instructor A's (id=10) course.
        // Without this check, any authenticated INSTRUCTOR could publish any course by ID.
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course)); // owned by id=10

        assertThatThrownBy(() -> courseService.publish(1L, otherInstructor)) // caller id=99
                .isInstanceOf(AccessDeniedException.class);

        // Nothing downstream should execute
        verify(moduleRepository, never()).findAllByCourse_IdOrderByOrderIndexAsc(any());
        verify(lessonRepository, never()).findAllByModule_IdOrderByOrderIndexAsc(any());
        verify(courseRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void publish_callerIdMatchesCourseOwner_proceedsNormally() {
        // Positive case: the owner must be allowed through the ownership gate
        stubValidPublish();
        assertThatCode(() -> courseService.publish(1L, instructor)).doesNotThrowAnyException();
    }

    // ── SECURITY: empty-course bypass attempts ────────────────────────────────

    @Test
    void publish_courseWithNoModules_throwsCoursePublishException() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> courseService.publish(1L, instructor))
                .isInstanceOf(CoursePublishException.class)
                .hasMessageContaining("at least one module");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void publish_moduleHasNoLessons_throwsCoursePublishException() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of(module1));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> courseService.publish(1L, instructor))
                .isInstanceOf(CoursePublishException.class)
                .hasMessageContaining("at least one lesson");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void publish_firstModuleHasLessonsButSecondDoesNot_throwsCoursePublishException() {
        // Verifies every module is validated, not just the first one.
        // A partial check would allow empty trailing modules to slip through.
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(module1, module2));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(lesson1));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(2L))
                .thenReturn(List.of()); // second module empty

        assertThatThrownBy(() -> courseService.publish(1L, instructor))
                .isInstanceOf(CoursePublishException.class)
                .hasMessageContaining(module2.getTitle()); // error names the offending module

        verify(courseRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any());
    }

    // ── Happy path: DB state ──────────────────────────────────────────────────

    @Test
    void publish_validCourse_returnsResponseWithPublishedStatus() {
        stubValidPublish();

        CourseResponse result = courseService.publish(1L, instructor);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.publishedAt()).isNotNull();
    }

    @Test
    void publish_validCourse_persistsPublishedStatusAndTimestampToDB() {
        stubValidPublish();

        courseService.publish(1L, instructor);

        verify(courseRepository).save(argThat(c ->
                c.getStatus() == CourseStatus.PUBLISHED && c.getPublishedAt() != null));
    }

    // ── Happy path: outbox event routing ─────────────────────────────────────

    @Test
    void publish_validCourse_outboxEventRoutedToCorrectTopicAndKey() {
        stubValidPublish();

        courseService.publish(1L, instructor);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key   = ArgumentCaptor.forClass(String.class);
        verify(outboxService).publish(topic.capture(), key.capture(), any());

        assertThat(topic.getValue()).isEqualTo("course.published");
        // Key is the courseId — used for Kafka partition assignment
        assertThat(key.getValue()).isEqualTo("1");
    }

    // ── Happy path: event schema (kafka-events.md §4.1) ──────────────────────

    @Test
    void publish_validCourse_eventEnvelopeHasCorrectMetadata() {
        stubValidPublish();
        courseService.publish(1L, instructor);

        CoursePublishedEvent event = captureEvent();

        assertThat(event.eventType()).isEqualTo("course.published");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.courseId()).isEqualTo(1L);
        assertThat(event.title()).isEqualTo("Java 101");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void publish_validCourse_eventIdIsRandomUuidPerCall() {
        // Event IDs must be unpredictable UUIDs — not sequential IDs or timestamps
        // that could be guessed or replayed by an attacker.
        stubValidPublish();
        courseService.publish(1L, instructor);
        CoursePublishedEvent e1 = captureEvent();

        // Reset and publish again to get a second event
        reset(outboxService);
        stubValidPublish();
        courseService.publish(1L, instructor);
        CoursePublishedEvent e2 = captureEvent();

        assertThat(e1.eventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(e2.eventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        // Each publish call must generate a fresh, unique event ID
        assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }

    @Test
    void publish_validCourse_eventInstructorMatchesCourseOwnerNotCaller() {
        // Instructor identity in the event must come from the DB user record,
        // not from an untrusted caller-supplied field.
        stubValidPublish();

        courseService.publish(1L, instructor);

        CoursePublishedEvent event = captureEvent();
        assertThat(event.instructor().id()).isEqualTo(10L);
        assertThat(event.instructor().fullName()).isEqualTo("Alice Smith");
    }

    @Test
    void publish_validCourse_eventContainsAllLessonsFromAllModules() {
        // All 3 lessons (2 in module1, 1 in module2) must appear in the event.
        // A partial snapshot would break downstream course-catalog consumers.
        stubValidPublish();

        courseService.publish(1L, instructor);

        CoursePublishedEvent event = captureEvent();
        assertThat(event.lessons()).hasSize(3);
        assertThat(event.lessons())
                .extracting(CoursePublishedEvent.LessonSummary::lessonId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void publish_validCourse_lessonSummariesCarryCorrectModuleContext() {
        stubValidPublish();

        courseService.publish(1L, instructor);

        CoursePublishedEvent event = captureEvent();

        CoursePublishedEvent.LessonSummary first = event.lessons().get(0);
        assertThat(first.lessonId()).isEqualTo(1L);
        assertThat(first.moduleId()).isEqualTo(1L);
        assertThat(first.moduleTitle()).isEqualTo("Intro");
        assertThat(first.contentType()).isEqualTo("VIDEO");

        // Third lesson belongs to module2 — verify module context switches correctly
        CoursePublishedEvent.LessonSummary third = event.lessons().get(2);
        assertThat(third.lessonId()).isEqualTo(3L);
        assertThat(third.moduleId()).isEqualTo(2L);
        assertThat(third.moduleTitle()).isEqualTo("Advanced");
        assertThat(third.contentType()).isEqualTo("ARTICLE");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void publish_alreadyPublishedCourse_canRepublishWithFreshEvent() {
        // No guard against double-publish by design — consumer-side idempotency
        // via idempotency_log deduplicates using the per-call UUID eventId.
        course.setStatus(CourseStatus.PUBLISHED);
        stubValidPublish();

        assertThatCode(() -> courseService.publish(1L, instructor)).doesNotThrowAnyException();
        verify(outboxService).publish(eq("course.published"), any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubValidPublish() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(module1, module2));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(lesson1, lesson2));
        when(lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(2L))
                .thenReturn(List.of(lesson3));
        when(userRepository.findById(10L)).thenReturn(Optional.of(instructor));
    }

    private CoursePublishedEvent captureEvent() {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(any(), any(), cap.capture());
        return (CoursePublishedEvent) cap.getValue();
    }
}
