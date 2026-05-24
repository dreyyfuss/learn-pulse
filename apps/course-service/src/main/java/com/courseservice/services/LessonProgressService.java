package com.courseservice.services;

import com.courseservice.dto.response.LessonCompleteResponse;
import com.courseservice.exception.LessonOutOfOrderException;
import com.courseservice.exception.ModuleLockedForUserException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.*;
import com.courseservice.models.Module;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ModuleProgressChecker moduleProgressChecker;
    private final StreakService streakService;

    @Caching(evict = {
            @CacheEvict(cacheNames = "analytics:instructor", allEntries = true),
            @CacheEvict(cacheNames = "analytics:admin",      key = "'platform'")
    })
    @Transactional
    public LessonCompleteResponse complete(UUID lessonId, UUID userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));

        Module module = lesson.getModule();
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

        // Idempotent: if already completed, skip re-recording but still check module progress
        Optional<LessonProgress> existingOpt = lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId);
        if (existingOpt.isPresent()) {
            ModuleProgressChecker.ModuleProgressResult result = moduleProgressChecker.tryComplete(module, enrolment);
            return new LessonCompleteResponse(lessonId, existingOpt.get().getCompletedAt(),
                    result.nextModuleId(), result.courseCompleted());
        }

        LessonProgress progress = new LessonProgress();
        progress.setUserId(userId);
        progress.setLesson(lesson);
        lessonProgressRepository.save(progress);

        streakService.recordActivity(userId);

        ModuleProgressChecker.ModuleProgressResult result = moduleProgressChecker.tryComplete(module, enrolment);
        return new LessonCompleteResponse(lessonId, progress.getCompletedAt(),
                result.nextModuleId(), result.courseCompleted());
    }
}
