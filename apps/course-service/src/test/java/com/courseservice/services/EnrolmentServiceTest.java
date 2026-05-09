package com.courseservice.services;

import com.courseservice.dto.request.AdminEnrolRequest;
import com.courseservice.dto.request.EnrolRequest;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.StartEnrolmentResponse;
import com.courseservice.events.dto.UserEnrolledEvent;
import com.courseservice.exception.AlreadyEnrolledException;
import com.courseservice.exception.EnrolmentCodeInvalidException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrolmentServiceTest {

    @Mock UserRepository userRepository;
    @Mock CourseRepository courseRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock LessonProgressRepository lessonProgressRepository;
    @Mock LessonRepository lessonRepository;
    @Mock OutboxService outboxService;

    @InjectMocks EnrolmentService enrolmentService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    User learner;
    Course publicCourse;
    Course privateCourse;
    Enrolment savedEnrolment;
    Module firstModule;

    @BeforeEach
    void setUp() {
        learner = User.builder().id(1L).fullName("Jane Doe")
                .roles(new HashSet<>(Set.of(Role.LEARNER))).build();

        publicCourse = Course.builder().id(10L).instructorId(99L).title("Java 101")
                .status(CourseStatus.PUBLISHED).visibility(CourseVisibility.PUBLIC).build();

        privateCourse = Course.builder().id(20L).instructorId(99L).title("Secret Course")
                .status(CourseStatus.PUBLISHED).visibility(CourseVisibility.PRIVATE)
                .enrolmentCode("SECRET123").build();

        savedEnrolment = Enrolment.builder().id(100L).user(learner).course(publicCourse)
                .status(EnrolmentStatus.ACTIVE).build();

        firstModule = Module.builder().id(1L).course(publicCourse)
                .title("Module 1").orderIndex(0).build();
    }

    // ── enrol() — resource-existence guards ───────────────────────────────────

    @Test
    void enrol_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(10L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void enrol_courseNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(10L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Course not found");

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void enrol_courseIsDraft_treatedAsNotFoundToPreventCourseEnumeration() {
        // SECURITY: returning a distinct "not published" error would let an attacker
        // enumerate valid course IDs — must surface the same ResourceNotFoundException.
        Course draft = Course.builder().id(10L).instructorId(99L).title("Draft")
                .status(CourseStatus.DRAFT).visibility(CourseVisibility.PUBLIC).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(10L, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    // ── enrol() — duplicate-enrolment guards ──────────────────────────────────

    @Test
    void enrol_alreadyEnrolled_throwsAlreadyEnrolledException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(publicCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(10L, null)))
                .isInstanceOf(AlreadyEnrolledException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void enrol_raceConditionConcurrentRequest_databaseConstraintCaughtAndNoEventEmitted() {
        // Two concurrent requests both pass existsBy, but the second hits the DB unique constraint.
        // The DataIntegrityViolationException must be caught and converted — no event emitted.
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(publicCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(enrolmentRepository.save(any())).thenThrow(new DataIntegrityViolationException("UK"));

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(10L, null)))
                .isInstanceOf(AlreadyEnrolledException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    // ── enrol() — private-course access control ───────────────────────────────

    @Test
    void enrol_privateCourseWithNullCode_throwsEnrolmentCodeInvalid() {
        // SECURITY: null code must be explicitly rejected — prevents enrolment without a key
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(20L)).thenReturn(Optional.of(privateCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 20L)).thenReturn(false);

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(20L, null)))
                .isInstanceOf(EnrolmentCodeInvalidException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void enrol_privateCourseWithWrongCode_throwsEnrolmentCodeInvalid() {
        // SECURITY: wrong code must be rejected — prevents brute-force guessing of course codes
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(20L)).thenReturn(Optional.of(privateCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 20L)).thenReturn(false);

        assertThatThrownBy(() -> enrolmentService.enrol(1L, new EnrolRequest(20L, "WRONG_CODE")))
                .isInstanceOf(EnrolmentCodeInvalidException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void enrol_privateCourseWithCorrectCode_enrollsAndEmitsEvent() {
        Enrolment saved = Enrolment.builder().id(200L).user(learner).course(privateCourse)
                .status(EnrolmentStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(20L)).thenReturn(Optional.of(privateCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 20L)).thenReturn(false);
        when(enrolmentRepository.save(any())).thenReturn(saved);

        enrolmentService.enrol(1L, new EnrolRequest(20L, "SECRET123"));

        verify(outboxService).publish(eq("user.enrolled"), any(), any());
    }

    // ── enrol() — happy path: outbox event emission ───────────────────────────

    @Test
    void enrol_publicCourse_emitsEventOnCorrectTopicWithUserIdAsKey() {
        stubEnrolSuccess();

        enrolmentService.enrol(1L, new EnrolRequest(10L, null));

        // Topic = "user.enrolled"; key = userId string (used for Kafka partition assignment)
        verify(outboxService).publish(eq("user.enrolled"), eq("1"), any());
    }

    @Test
    void enrol_publicCourse_eventSchemaMatchesKafkaSpec() {
        stubEnrolSuccess();

        enrolmentService.enrol(1L, new EnrolRequest(10L, null));

        UserEnrolledEvent event = captureEnrolEvent();

        // kafka-events.md §4.1 envelope fields
        assertThat(event.eventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(event.eventType()).isEqualTo("user.enrolled");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.occurredAt()).isNotNull();

        // Payload fields
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.courseId()).isEqualTo(10L);
        assertThat(event.enrolmentId()).isEqualTo(100L); // id returned by repository save
    }

    @Test
    void enrol_publicCourse_eachCallProducesUniqueEventId() {
        // Reusing an eventId would cause consumer deduplication to silently drop real events
        stubEnrolSuccess();
        enrolmentService.enrol(1L, new EnrolRequest(10L, null));
        UserEnrolledEvent first = captureEnrolEvent();

        reset(outboxService);
        stubEnrolSuccess();
        enrolmentService.enrol(1L, new EnrolRequest(10L, null));
        UserEnrolledEvent second = captureEnrolEvent();

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    void enrol_publicCourse_returnsCorrectEnrolmentResponse() {
        stubEnrolSuccess();

        EnrolmentResponse result = enrolmentService.enrol(1L, new EnrolRequest(10L, null));

        assertThat(result.enrolmentId()).isEqualTo(100L);
        assertThat(result.courseId()).isEqualTo(10L);
        assertThat(result.courseTitle()).isEqualTo("Java 101");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    // ── adminEnrol() — outbox event emission ─────────────────────────────────

    @Test
    void adminEnrol_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrolmentService.adminEnrol(new AdminEnrolRequest(1L, 10L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void adminEnrol_courseNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrolmentService.adminEnrol(new AdminEnrolRequest(1L, 10L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void adminEnrol_alreadyEnrolled_throwsAlreadyEnrolledException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(publicCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> enrolmentService.adminEnrol(new AdminEnrolRequest(1L, 10L)))
                .isInstanceOf(AlreadyEnrolledException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void adminEnrol_success_emitsUserEnrolledEventViaOutbox() {
        // Admin path must emit the same event as self-enrolment — no silent bypass
        stubAdminEnrolSuccess();

        enrolmentService.adminEnrol(new AdminEnrolRequest(1L, 10L));

        verify(outboxService).publish(eq("user.enrolled"), eq("1"), any());
    }

    @Test
    void adminEnrol_success_eventSchemaMatchesKafkaSpec() {
        stubAdminEnrolSuccess();

        enrolmentService.adminEnrol(new AdminEnrolRequest(1L, 10L));

        UserEnrolledEvent event = captureEnrolEvent();

        assertThat(event.eventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(event.eventType()).isEqualTo("user.enrolled");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.courseId()).isEqualTo(10L);
        assertThat(event.enrolmentId()).isEqualTo(100L);
    }

    @Test
    void adminEnrol_raceConditionDuplicate_throwsAlreadyEnrolledException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(publicCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(enrolmentRepository.save(any())).thenThrow(new DataIntegrityViolationException("UK"));

        assertThatThrownBy(() -> enrolmentService.adminEnrol(new AdminEnrolRequest(1L, 10L)))
                .isInstanceOf(AlreadyEnrolledException.class);

        verify(outboxService, never()).publish(any(), any(), any());
    }

    // ── start() — security and behaviour ──────────────────────────────────────

    @Test
    void start_enrolmentNotFound_throwsResourceNotFoundException() {
        when(enrolmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrolmentService.start(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void start_callerDoesNotOwnEnrolment_throwsAccessDeniedException() {
        // SECURITY: learner must not be able to start another learner's enrolment by guessing IDs
        Enrolment enrolment = Enrolment.builder().id(100L).user(learner).course(publicCourse).build();
        when(enrolmentRepository.findById(100L)).thenReturn(Optional.of(enrolment));

        // caller id=2 does not match enrolment owner id=1
        assertThatThrownBy(() -> enrolmentService.start(100L, 2L))
                .isInstanceOf(AccessDeniedException.class);

        verify(moduleUnlockRepository, never()).save(any());
        verify(enrolmentRepository, never()).save(any());
    }

    @Test
    void start_courseHasNoModules_throwsResourceNotFoundException() {
        Enrolment enrolment = Enrolment.builder().id(100L).user(learner).course(publicCourse).build();
        when(enrolmentRepository.findById(100L)).thenReturn(Optional.of(enrolment));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> enrolmentService.start(100L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no modules");
    }

    @Test
    void start_validEnrolment_setsStartedAtAndUnlocksFirstModule() {
        Enrolment enrolment = Enrolment.builder().id(100L).user(learner).course(publicCourse).build();
        when(enrolmentRepository.findById(100L)).thenReturn(Optional.of(enrolment));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(10L))
                .thenReturn(List.of(firstModule));

        StartEnrolmentResponse result = enrolmentService.start(100L, 1L);

        assertThat(result.startedAt()).isNotNull();
        assertThat(result.unlockedModuleId()).isEqualTo(1L);

        ArgumentCaptor<ModuleUnlock> cap = ArgumentCaptor.forClass(ModuleUnlock.class);
        verify(moduleUnlockRepository).save(cap.capture());
        assertThat(cap.getValue().getModule().getId()).isEqualTo(1L);
        assertThat(cap.getValue().getEnrolment().getId()).isEqualTo(100L);
    }

    @Test
    void start_alreadyStarted_idempotentAndNoWritesOccur() {
        // Repeated start calls must return the original timestamp and skip all writes
        Enrolment enrolment = Enrolment.builder().id(100L).user(learner).course(publicCourse)
                .startedAt(LocalDateTime.now().minusDays(1)).build();
        when(enrolmentRepository.findById(100L)).thenReturn(Optional.of(enrolment));
        when(moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(10L))
                .thenReturn(List.of(firstModule));

        StartEnrolmentResponse result = enrolmentService.start(100L, 1L);

        assertThat(result.unlockedModuleId()).isEqualTo(1L);
        verify(enrolmentRepository, never()).save(any());
        verify(moduleUnlockRepository, never()).save(any());
        verify(courseRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubEnrolSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(publicCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(enrolmentRepository.save(any())).thenReturn(savedEnrolment);
    }

    private void stubAdminEnrolSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(learner));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(publicCourse));
        when(enrolmentRepository.existsByUser_IdAndCourse_Id(1L, 10L)).thenReturn(false);
        when(enrolmentRepository.save(any())).thenReturn(savedEnrolment);
    }

    private UserEnrolledEvent captureEnrolEvent() {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(any(), any(), cap.capture());
        return (UserEnrolledEvent) cap.getValue();
    }
}
