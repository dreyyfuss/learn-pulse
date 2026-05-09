package com.courseservice.services;

import com.courseservice.dto.response.CompleteLessonResponse;
import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.events.dto.CourseCompletedEvent;
import com.courseservice.events.dto.ModuleUnlockedEvent;
import com.courseservice.exception.LessonOutOfOrderException;
import com.courseservice.exception.ModuleLockedException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;

    @Transactional
    public CompleteLessonResponse completeLesson(Long lessonId, Long userId) {
        // Resolve lesson → module → course
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found."));
        Long moduleId = lesson.getModule().getId();

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found."));
        Long courseId = module.getCourse().getId();

        // Step 1 — active, started enrolment must exist
        Enrolment enrolment = enrolmentRepository.findByUser_IdAndCourse_Id(userId, courseId)
                .orElseThrow(() -> new AccessDeniedException("You are not enrolled in this course."));

        if (enrolment.getStartedAt() == null) {
            throw new AccessDeniedException("You must start the course before completing lessons.");
        }

        // Step 2 — lesson's module must be unlocked for this enrolment
        if (!moduleUnlockRepository.existsByEnrolment_IdAndModule_Id(enrolment.getId(), moduleId)) {
            throw new ModuleLockedException("This module is not yet unlocked.");
        }

        // Load ordered lesson list once — used for both out-of-order check and last-lesson check
        List<Lesson> moduleLessons = lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(moduleId);

        // Idempotent guard — already completed, skip all side effects
        if (lessonProgressRepository.existsByUser_IdAndLesson_Id(userId, lessonId)) {
            LessonProgress existing = lessonProgressRepository
                    .findByUser_IdAndLesson_Id(userId, lessonId).orElseThrow();
            return new CompleteLessonResponse(lessonId, existing.getCompletedAt(), null, null);
        }

        // Step 3 — every lesson that precedes this one (lower orderIndex) must be COMPLETED
        for (Lesson l : moduleLessons) {
            if (l.getId().equals(lessonId)) break;
            if (!lessonProgressRepository.existsByUser_IdAndLesson_Id(userId, l.getId())) {
                throw new LessonOutOfOrderException("Complete all previous lessons in this module first.");
            }
        }

        // Step 4 — insert lesson_progress row
        LessonProgress progress = lessonProgressRepository.save(LessonProgress.builder()
                .user(userRepository.getReferenceById(userId))
                .lesson(lesson)
                .build());

        // Step 5 — determine side effects if this was the last lesson in the module
        boolean isLastLesson = moduleLessons.get(moduleLessons.size() - 1).getId().equals(lessonId);
        if (!isLastLesson) {
            return new CompleteLessonResponse(lessonId, progress.getCompletedAt(), null, null);
        }

        List<Module> courseModules = moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(courseId);
        Module nextModule = courseModules.stream()
                .filter(m -> m.getOrderIndex() > module.getOrderIndex())
                .findFirst()
                .orElse(null);

        if (nextModule != null) {
            // Unlock the next module
            moduleUnlockRepository.save(ModuleUnlock.builder()
                    .enrolment(enrolment)
                    .module(nextModule)
                    .build());
            outboxService.publish("module.unlocked", String.valueOf(userId),
                    new ModuleUnlockedEvent(UUID.randomUUID().toString(), "module.unlocked", 1,
                            progress.getCompletedAt().toString(),
                            userId, courseId, enrolment.getId(),
                            nextModule.getId(), nextModule.getTitle(), nextModule.getOrderIndex()));
            return new CompleteLessonResponse(lessonId, progress.getCompletedAt(), nextModule.getId(), null);
        }

        // Final module — mark enrolment COMPLETED
        enrolment.setStatus(EnrolmentStatus.COMPLETED);
        enrolment.setCompletedAt(progress.getCompletedAt());
        enrolmentRepository.save(enrolment);
        outboxService.publish("course.completed", String.valueOf(enrolment.getId()),
                new CourseCompletedEvent(UUID.randomUUID().toString(), "course.completed", 1,
                        progress.getCompletedAt().toString(),
                        userId, courseId, enrolment.getId(),
                        progress.getCompletedAt().toString()));
        return new CompleteLessonResponse(lessonId, progress.getCompletedAt(), null, true);
    }

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(Long enrolmentId, User caller) {
        Enrolment enrolment = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found."));

        Long courseId = enrolment.getCourse().getId();
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found."));

        // LEARNER (owner) | INSTRUCTOR (course owner) | ADMIN
        boolean isOwner      = enrolment.getUser().getId().equals(caller.getId());
        boolean isInstructor = caller.getRoles().contains(Role.INSTRUCTOR)
                               && course.getInstructorId().equals(caller.getId());
        boolean isAdmin      = caller.getRoles().contains(Role.ADMIN);

        if (!isOwner && !isInstructor && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view this progress.");
        }

        Long learnerId = enrolment.getUser().getId();

        // Bulk-fetch unlock and completion state — avoids per-module/per-lesson queries
        Set<Long> unlockedModuleIds = moduleUnlockRepository
                .findAllByEnrolment_Id(enrolmentId)
                .stream()
                .map(u -> u.getModule().getId())
                .collect(Collectors.toSet());

        Set<Long> completedLessonIds = lessonProgressRepository
                .findAllByUser_Id(learnerId)
                .stream()
                .map(p -> p.getLesson().getId())
                .collect(Collectors.toSet());

        List<Module> modules = moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(courseId);

        Long currentLessonId = null;
        List<ProgressResponse.ModuleProgress> moduleProgressList = new ArrayList<>();

        for (Module mod : modules) {
            boolean unlocked = unlockedModuleIds.contains(mod.getId());
            List<Lesson> lessons = lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(mod.getId());

            List<ProgressResponse.LessonItem> lessonItems = new ArrayList<>();
            for (Lesson l : lessons) {
                boolean completed = completedLessonIds.contains(l.getId());
                lessonItems.add(new ProgressResponse.LessonItem(
                        l.getId(), l.getTitle(), l.getOrderIndex(),
                        l.getContentType().name(), completed));

                // First incomplete lesson in the first unlocked module
                if (unlocked && !completed && currentLessonId == null) {
                    currentLessonId = l.getId();
                }
            }

            moduleProgressList.add(new ProgressResponse.ModuleProgress(
                    mod.getId(), mod.getTitle(), mod.getOrderIndex(), unlocked, lessonItems));
        }

        return new ProgressResponse(
                enrolment.getId(),
                courseId,
                course.getTitle(),
                enrolment.getStatus().name(),
                enrolment.getEnrolledAt(),
                enrolment.getStartedAt(),
                enrolment.getCompletedAt(),
                moduleProgressList,
                currentLessonId);
    }
}
