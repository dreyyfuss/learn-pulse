package com.courseservice.services;

import com.courseservice.dto.response.LessonCompleteResponse;
import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.events.dto.CourseCompletedEvent;
import com.courseservice.events.dto.ModuleUnlockedEvent;
import com.courseservice.events.producers.CourseEventProducer;
import com.courseservice.exception.LessonOutOfOrderException;
import com.courseservice.exception.ModuleLockedForUserException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonProgressService {

    private final LessonRepository lessonRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final ModuleRepository moduleRepository;
    private final CourseEventProducer courseEventProducer;

    @Transactional
    public LessonCompleteResponse complete(UUID lessonId, UUID userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));

        com.courseservice.models.Module module = lesson.getModule();
        UUID courseId = module.getCourse().getId();

        Enrolment enrolment = enrolmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("No active enrolment found for this course."));

        moduleUnlockRepository.findByEnrolmentIdAndModuleId(enrolment.getId(), module.getId())
                .orElseThrow(() -> new ModuleLockedForUserException("This module is not yet unlocked."));

        List<Lesson> moduleLessons = lessonRepository.findByModuleIdOrderByOrderIndex(module.getId());

        List<UUID> prerequisiteIds = moduleLessons.stream()
                .filter(l -> l.getOrderIndex() < lesson.getOrderIndex())
                .map(Lesson::getId)
                .collect(Collectors.toList());

        if (!prerequisiteIds.isEmpty()) {
            long completed = lessonProgressRepository.findByUserIdAndLessonIdIn(userId, prerequisiteIds).size();
            if (completed < prerequisiteIds.size()) {
                throw new LessonOutOfOrderException("Complete all previous lessons in this module first.");
            }
        }

        // Idempotent: if already completed, return current state
        Optional<LessonProgress> existingOpt = lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId);
        if (existingOpt.isPresent()) {
            return idempotentResponse(existingOpt.get(), lesson, module, moduleLessons, enrolment, courseId);
        }

        // Record new completion
        LessonProgress progress = new LessonProgress();
        progress.setUserId(userId);
        progress.setLesson(lesson);
        lessonProgressRepository.save(progress);

        boolean isLastLesson = moduleLessons.stream().noneMatch(l -> l.getOrderIndex() > lesson.getOrderIndex());
        if (!isLastLesson) {
            return new LessonCompleteResponse(lessonId, progress.getCompletedAt(), null, false);
        }

        // Last lesson in module — determine next module or course completion
        return handleLastLesson(progress, lesson, module, enrolment, courseId);
    }

    private LessonCompleteResponse idempotentResponse(LessonProgress existing, Lesson lesson,
                                                       com.courseservice.models.Module module,
                                                       List<Lesson> moduleLessons,
                                                       Enrolment enrolment, UUID courseId) {
        boolean isLastLesson = moduleLessons.stream().noneMatch(l -> l.getOrderIndex() > lesson.getOrderIndex());
        if (!isLastLesson) {
            return new LessonCompleteResponse(lesson.getId(), existing.getCompletedAt(), null, false);
        }
        if (enrolment.getStatus() == EnrolmentStatus.COMPLETED) {
            return new LessonCompleteResponse(lesson.getId(), existing.getCompletedAt(), null, true);
        }
        com.courseservice.models.Module nextModule = moduleRepository
                .findByCourseIdOrderByOrderIndex(courseId).stream()
                .filter(m -> m.getOrderIndex() > module.getOrderIndex())
                .findFirst().orElse(null);
        UUID nextModuleId = nextModule != null ? nextModule.getId() : null;
        return new LessonCompleteResponse(lesson.getId(), existing.getCompletedAt(), nextModuleId, false);
    }

    private LessonCompleteResponse handleLastLesson(LessonProgress progress, Lesson lesson,
                                                     com.courseservice.models.Module module,
                                                     Enrolment enrolment, UUID courseId) {
        com.courseservice.models.Module nextModule = moduleRepository
                .findByCourseIdOrderByOrderIndex(courseId).stream()
                .filter(m -> m.getOrderIndex() > module.getOrderIndex())
                .findFirst().orElse(null);

        String now = Instant.now().toString();

        if (nextModule != null) {
            ModuleUnlock unlock = new ModuleUnlock();
            unlock.setEnrolment(enrolment);
            unlock.setModule(nextModule);
            moduleUnlockRepository.save(unlock);

            courseEventProducer.emitModuleUnlocked(
                    ModuleUnlockedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .occurredAt(now)
                            .userId(enrolment.getUserId())
                            .courseId(courseId)
                            .enrolmentId(enrolment.getId())
                            .unlockedModuleId(nextModule.getId())
                            .unlockedModuleTitle(nextModule.getTitle())
                            .unlockedModuleOrder(nextModule.getOrderIndex())
                            .build()
            );

            return new LessonCompleteResponse(lesson.getId(), progress.getCompletedAt(), nextModule.getId(), false);
        }

        enrolment.setStatus(EnrolmentStatus.COMPLETED);
        enrolment.setCompletedAt(LocalDateTime.now());
        enrolmentRepository.save(enrolment);

        courseEventProducer.emitCourseCompleted(
                CourseCompletedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .occurredAt(now)
                        .userId(enrolment.getUserId())
                        .courseId(courseId)
                        .enrolmentId(enrolment.getId())
                        .completedAt(now)
                        .build()
        );

        return new LessonCompleteResponse(lesson.getId(), progress.getCompletedAt(), null, true);
    }
}
