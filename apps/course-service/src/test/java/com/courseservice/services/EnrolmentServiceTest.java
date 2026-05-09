package com.courseservice.services;

import com.courseservice.dto.request.AdminEnrolRequest;
import com.courseservice.dto.request.EnrolRequest;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.EnrolmentSummaryResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.dto.response.StartEnrolmentResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.exception.AlreadyEnrolledException;
import com.courseservice.exception.EnrolmentCodeInvalidException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.models.Course;
import com.courseservice.models.Enrolment;
import com.courseservice.models.ModuleUnlock;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LessonProgressRepository;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import com.courseservice.repositories.ModuleUnlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrolmentServiceTest {

    @Mock CourseRepository courseRepository;
    @Mock EnrolmentRepository enrolmentRepository;
    @Mock ModuleRepository moduleRepository;
    @Mock ModuleUnlockRepository moduleUnlockRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonProgressRepository lessonProgressRepository;

    @InjectMocks EnrolmentService enrolmentService;

    private static final UUID USER_ID       = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENROLMENT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MODULE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void enrol_publicCourse_createsEnrolment() {
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(enrolmentRepository.existsByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(false);
        when(enrolmentRepository.save(any(Enrolment.class))).thenAnswer(inv -> {
            Enrolment e = inv.getArgument(0);
            e.setId(ENROLMENT_ID);
            return e;
        });

        EnrolmentResponse response = enrolmentService.enrol(new EnrolRequest(COURSE_ID, null), USER_ID);

        assertThat(response.enrolmentId()).isEqualTo(ENROLMENT_ID);
        assertThat(response.status()).isEqualTo(EnrolmentStatus.ACTIVE);
        assertThat(response.startedAt()).isNull();
    }

    @Test
    void enrol_privateCourse_withValidCode_succeeds() {
        Course course = publishedCourse(CourseVisibility.PRIVATE, "MYCODE12");
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(enrolmentRepository.existsByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(false);
        when(enrolmentRepository.save(any(Enrolment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrolmentResponse response = enrolmentService.enrol(new EnrolRequest(COURSE_ID, "MYCODE12"), USER_ID);

        assertThat(response).isNotNull();
    }

    @Test
    void enrol_privateCourse_withInvalidCode_throwsEnrolmentCodeInvalid() {
        Course course = publishedCourse(CourseVisibility.PRIVATE, "MYCODE12");
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(enrolmentRepository.existsByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(false);

        assertThatThrownBy(() -> enrolmentService.enrol(new EnrolRequest(COURSE_ID, "WRONGCOD"), USER_ID))
                .isInstanceOf(EnrolmentCodeInvalidException.class);
    }

    @Test
    void enrol_duplicateEnrolment_throwsAlreadyEnrolled() {
        when(courseRepository.findById(COURSE_ID))
                .thenReturn(Optional.of(publishedCourse(CourseVisibility.PUBLIC, null)));
        when(enrolmentRepository.existsByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(true);

        assertThatThrownBy(() -> enrolmentService.enrol(new EnrolRequest(COURSE_ID, null), USER_ID))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    void start_firstCall_setsStartedAtAndLocksAndUnlocksModule1() {
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        Enrolment enrolment = enrolmentWithCourse(course, null);

        com.courseservice.models.Module module = buildModule(MODULE_ID, 1);

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(moduleRepository.findByCourseIdOrderByOrderIndex(COURSE_ID)).thenReturn(List.of(module));
        when(enrolmentRepository.save(any(Enrolment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        StartEnrolmentResponse response = enrolmentService.start(ENROLMENT_ID, USER_ID);

        assertThat(response.startedAt()).isNotNull();
        assertThat(response.unlockedModuleId()).isEqualTo(MODULE_ID);

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(courseCaptor.capture());
        assertThat(courseCaptor.getValue().isLocked()).isTrue();
        assertThat(courseCaptor.getValue().getLockedAt()).isNotNull();

        verify(moduleUnlockRepository).save(any(ModuleUnlock.class));
    }

    @Test
    void start_secondCall_idempotentReturnsSameStartedAt() {
        LocalDateTime existingTime = LocalDateTime.now().minusDays(1);
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        Enrolment enrolment = enrolmentWithCourse(course, existingTime);

        com.courseservice.models.Module module = buildModule(MODULE_ID, 1);
        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));
        when(moduleRepository.findByCourseIdOrderByOrderIndex(COURSE_ID)).thenReturn(List.of(module));

        StartEnrolmentResponse response = enrolmentService.start(ENROLMENT_ID, USER_ID);

        assertThat(response.startedAt()).isEqualTo(existingTime);
        assertThat(response.unlockedModuleId()).isEqualTo(MODULE_ID);

        verify(enrolmentRepository, never()).save(any());
        verify(moduleUnlockRepository, never()).save(any());
    }

    @Test
    void start_notOwner_throwsNotOwner() {
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        Enrolment enrolment = new Enrolment();
        enrolment.setId(ENROLMENT_ID);
        enrolment.setUserId(UUID.randomUUID()); // different user
        enrolment.setCourse(course);

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));

        assertThatThrownBy(() -> enrolmentService.start(ENROLMENT_ID, USER_ID))
                .isInstanceOf(NotOwnerException.class);
    }

    @Test
    void listMyEnrolments_returnsPageWithProgressPercent() {
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        Enrolment enrolment = enrolmentWithCourse(course, null);

        PageRequest pageable = PageRequest.of(0, 20);
        when(enrolmentRepository.findByUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(enrolment), pageable, 1));
        when(lessonRepository.countByCourseId(COURSE_ID)).thenReturn(4L);
        when(lessonProgressRepository.countCompletedByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(2L);

        PageResponse<EnrolmentSummaryResponse> response = enrolmentService.listMyEnrolments(USER_ID, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).progressPercent()).isEqualTo(50);
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    void adminEnrol_createsEnrolmentForAnyUser() {
        UUID targetUserId = UUID.randomUUID();
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        course.setStatus(CourseStatus.DRAFT);  // admin can enrol even in draft

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(enrolmentRepository.existsByUserIdAndCourseId(targetUserId, COURSE_ID)).thenReturn(false);
        when(enrolmentRepository.save(any(Enrolment.class))).thenAnswer(inv -> {
            Enrolment e = inv.getArgument(0);
            e.setId(ENROLMENT_ID);
            return e;
        });

        EnrolmentResponse response = enrolmentService.adminEnrol(new AdminEnrolRequest(targetUserId, COURSE_ID));

        assertThat(response.enrolmentId()).isEqualTo(ENROLMENT_ID);
    }

    @Test
    void adminEnrol_duplicateStillThrows() {
        UUID targetUserId = UUID.randomUUID();
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(enrolmentRepository.existsByUserIdAndCourseId(targetUserId, COURSE_ID)).thenReturn(true);

        assertThatThrownBy(() -> enrolmentService.adminEnrol(new AdminEnrolRequest(targetUserId, COURSE_ID)))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    void adminUnenrol_deletesEnrolmentAndLessonProgress() {
        Course course = publishedCourse(CourseVisibility.PUBLIC, null);
        Enrolment enrolment = enrolmentWithCourse(course, null);

        when(enrolmentRepository.findById(ENROLMENT_ID)).thenReturn(Optional.of(enrolment));

        enrolmentService.adminUnenrol(ENROLMENT_ID);

        verify(lessonProgressRepository).deleteByUserIdAndCourseId(USER_ID, COURSE_ID);
        verify(enrolmentRepository).delete(enrolment);
    }

    // --- helpers ---

    private Course publishedCourse(CourseVisibility visibility, String code) {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setInstructorId(UUID.randomUUID());
        c.setTitle("Test Course");
        c.setVisibility(visibility);
        c.setStatus(CourseStatus.PUBLISHED);
        c.setEnrolmentCode(code);
        return c;
    }

    private Enrolment enrolmentWithCourse(Course course, LocalDateTime startedAt) {
        Enrolment e = new Enrolment();
        e.setId(ENROLMENT_ID);
        e.setUserId(USER_ID);
        e.setCourse(course);
        e.setStartedAt(startedAt);
        return e;
    }

    private com.courseservice.models.Module buildModule(UUID id, int orderIndex) {
        com.courseservice.models.Module m = new com.courseservice.models.Module();
        m.setId(id);
        m.setTitle("Module " + orderIndex);
        m.setOrderIndex(orderIndex);
        return m;
    }
}
