package com.courseservice.services;

import com.courseservice.dto.request.AdminEnrolRequest;
import com.courseservice.dto.request.EnrolRequest;
import com.courseservice.dto.response.EnrolmentResponse;
import com.courseservice.dto.response.EnrolmentSummaryResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.dto.response.StartEnrolmentResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.exception.AlreadyEnrolledException;
import com.courseservice.exception.EnrolmentCodeInvalidException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Enrolment;
import com.courseservice.models.ModuleUnlock;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LessonProgressRepository;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import com.courseservice.repositories.ModuleUnlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrolmentService {

    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;

    @Transactional
    public EnrolmentResponse enrol(EnrolRequest req, UUID userId) {
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + req.courseId()));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Course not found: " + req.courseId());
        }

        if (enrolmentRepository.existsByUserIdAndCourseId(userId, req.courseId())) {
            throw new AlreadyEnrolledException("You are already enrolled in this course.");
        }

        if (course.getVisibility() == CourseVisibility.PRIVATE) {
            if (req.enrolmentCode() == null || !req.enrolmentCode().equals(course.getEnrolmentCode())) {
                throw new EnrolmentCodeInvalidException("Invalid or missing enrolment code.");
            }
        }

        Enrolment enrolment = new Enrolment();
        enrolment.setUserId(userId);
        enrolment.setCourse(course);
        enrolmentRepository.save(enrolment);

        return EnrolmentResponse.from(enrolment);
    }

    @Transactional
    public StartEnrolmentResponse start(UUID enrolmentId, UUID userId) {
        Enrolment enrolment = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + enrolmentId));

        if (!enrolment.getUserId().equals(userId)) {
            throw new NotOwnerException("You do not own this enrolment.");
        }

        if (enrolment.getStartedAt() != null) {
            UUID firstModuleId = getFirstModuleId(enrolment.getCourse().getId());
            return new StartEnrolmentResponse(enrolment.getStartedAt(), firstModuleId);
        }

        LocalDateTime now = LocalDateTime.now();
        enrolment.setStartedAt(now);

        Course course = enrolment.getCourse();
        if (!course.isLocked()) {
            course.setLocked(true);
            course.setLockedAt(now);
            courseRepository.save(course);
        }

        com.courseservice.models.Module firstModule = getFirstModule(course.getId());
        ModuleUnlock unlock = new ModuleUnlock();
        unlock.setEnrolment(enrolment);
        unlock.setModule(firstModule);
        moduleUnlockRepository.save(unlock);

        enrolmentRepository.save(enrolment);

        return new StartEnrolmentResponse(now, firstModule.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrolmentSummaryResponse> listMyEnrolments(UUID userId, Pageable pageable) {
        Page<Enrolment> page = enrolmentRepository.findByUserId(userId, pageable);
        Page<EnrolmentSummaryResponse> mapped = page.map(e -> {
            UUID courseId = e.getCourse().getId();
            long total = lessonRepository.countByCourseId(courseId);
            long completed = lessonProgressRepository.countCompletedByUserIdAndCourseId(userId, courseId);
            int percent = total == 0 ? 0 : (int) Math.round(100.0 * completed / total);
            return EnrolmentSummaryResponse.from(e, percent);
        });
        return PageResponse.from(mapped);
    }

    @Transactional
    public EnrolmentResponse adminEnrol(AdminEnrolRequest req) {
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + req.courseId()));

        if (enrolmentRepository.existsByUserIdAndCourseId(req.userId(), req.courseId())) {
            throw new AlreadyEnrolledException("User is already enrolled in this course.");
        }

        Enrolment enrolment = new Enrolment();
        enrolment.setUserId(req.userId());
        enrolment.setCourse(course);
        enrolmentRepository.save(enrolment);

        return EnrolmentResponse.from(enrolment);
    }

    @Transactional
    public void adminUnenrol(UUID enrolmentId) {
        Enrolment enrolment = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + enrolmentId));

        UUID courseId = enrolment.getCourse().getId();
        lessonProgressRepository.deleteByUserIdAndCourseId(enrolment.getUserId(), courseId);
        enrolmentRepository.delete(enrolment);
    }

    private com.courseservice.models.Module getFirstModule(UUID courseId) {
        List<com.courseservice.models.Module> modules =
                moduleRepository.findByCourseIdOrderByOrderIndex(courseId);
        if (modules.isEmpty()) {
            throw new ResourceNotFoundException("Course has no modules.");
        }
        return modules.get(0);
    }

    private UUID getFirstModuleId(UUID courseId) {
        return getFirstModule(courseId).getId();
    }
}
