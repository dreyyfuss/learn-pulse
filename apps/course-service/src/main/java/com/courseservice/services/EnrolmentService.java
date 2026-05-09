package com.courseservice.services;

import com.courseservice.dto.request.AdminEnrolRequest;
import com.courseservice.dto.request.EnrolRequest;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.EnrolmentSummaryResponse;
import com.courseservice.dto.response.StartEnrolmentResponse;
import com.courseservice.events.dto.UserEnrolledEvent;
import com.courseservice.exception.AlreadyEnrolledException;
import com.courseservice.exception.EnrolmentCodeInvalidException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrolmentService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LessonRepository lessonRepository;
    private final OutboxService outboxService;

    @Transactional
    public EnrolmentResponse enrol(Long userId, EnrolRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found."));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Course not found.");
        }

        if (enrolmentRepository.existsByUser_IdAndCourse_Id(userId, course.getId())) {
            throw new AlreadyEnrolledException("You are already enrolled in this course.");
        }

        if (course.getVisibility() == CourseVisibility.PRIVATE) {
            String code = request.enrolmentCode();
            if (code == null || !code.equals(course.getEnrolmentCode())) {
                throw new EnrolmentCodeInvalidException("Invalid enrolment code.");
            }
        }

        Enrolment enrolment = Enrolment.builder()
                .user(user)
                .course(course)
                .build();

        try {
            enrolment = enrolmentRepository.save(enrolment);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: second concurrent request hits the DB unique constraint
            throw new AlreadyEnrolledException("You are already enrolled in this course.");
        }

        outboxService.publish("user.enrolled", String.valueOf(userId),
                new UserEnrolledEvent(UUID.randomUUID().toString(), "user.enrolled", 1,
                        LocalDateTime.now().toString(), userId, course.getId(), enrolment.getId()));

        return EnrolmentResponse.from(enrolment);
    }

    @Transactional
    public StartEnrolmentResponse start(Long enrolmentId, Long userId) {
        Enrolment enrolment = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found."));

        if (!enrolment.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not own this enrolment.");
        }

        Module firstModule = moduleRepository
                .findAllByCourse_IdOrderByOrderIndexAsc(enrolment.getCourse().getId())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Course has no modules."));

        // Idempotent: already started — return existing state without any writes
        if (enrolment.getStartedAt() != null) {
            return new StartEnrolmentResponse(enrolment.getStartedAt(), firstModule.getId());
        }

        LocalDateTime now = LocalDateTime.now();

        enrolment.setStartedAt(now);
        enrolmentRepository.save(enrolment);

        // Lock the course; multiple concurrent starts on the same course are safe because
        // the UPDATE is a no-op when is_locked is already 1
        Course course = enrolment.getCourse();
        if (!course.isLocked()) {
            course.setLocked(true);
            course.setLockedAt(now);
            courseRepository.save(course);
        }

        moduleUnlockRepository.save(ModuleUnlock.builder()
                .enrolment(enrolment)
                .module(firstModule)
                .build());

        return new StartEnrolmentResponse(now, firstModule.getId());
    }

    @Transactional
    public EnrolmentResponse adminEnrol(AdminEnrolRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found."));

        if (enrolmentRepository.existsByUser_IdAndCourse_Id(request.userId(), request.courseId())) {
            throw new AlreadyEnrolledException("User is already enrolled in this course.");
        }

        Enrolment enrolment;
        try {
            enrolment = enrolmentRepository.save(Enrolment.builder().user(user).course(course).build());
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException("User is already enrolled in this course.");
        }

        outboxService.publish("user.enrolled", String.valueOf(request.userId()),
                new UserEnrolledEvent(UUID.randomUUID().toString(), "user.enrolled", 1,
                        LocalDateTime.now().toString(),
                        request.userId(), request.courseId(), enrolment.getId()));

        return EnrolmentResponse.from(enrolment);
    }

    @Transactional
    public void adminUnenrol(Long enrolmentId) {
        Enrolment enrolment = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found."));

        Long userId   = enrolment.getUser().getId();
        Long courseId = enrolment.getCourse().getId();

        // lesson_progress has no FK to enrolments — must be cleaned up manually before the delete
        lessonProgressRepository.deleteByUserIdAndCourseId(userId, courseId);

        // module_unlocks cascade automatically via FK enrolment_id → enrolments.id ON DELETE CASCADE
        enrolmentRepository.delete(enrolment);
    }

    @Transactional(readOnly = true)
    public List<EnrolmentSummaryResponse> getMyEnrolments(Long userId) {
        return enrolmentRepository.findAllByUser_Id(userId).stream()
                .map(e -> toSummary(e, userId))
                .toList();
    }

    private EnrolmentSummaryResponse toSummary(Enrolment e, Long userId) {
        Long courseId = e.getCourse().getId();
        long completed = lessonProgressRepository.countCompletedByUserAndCourse(userId, courseId);
        long total     = lessonRepository.countByCourseId(courseId);
        int  percent   = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        return new EnrolmentSummaryResponse(
                e.getId(),
                courseId,
                e.getCourse().getTitle(),
                e.getStatus().name(),
                e.getEnrolledAt(),
                e.getStartedAt(),
                e.getCompletedAt(),
                completed,
                total,
                percent);
    }
}
