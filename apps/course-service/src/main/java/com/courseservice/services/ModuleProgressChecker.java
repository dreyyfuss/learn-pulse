package com.courseservice.services;

import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.events.dto.CourseCompletedEvent;
import com.courseservice.events.dto.ModuleUnlockedEvent;
import com.courseservice.events.producers.CourseEventProducer;
import com.courseservice.models.Enrolment;
import com.courseservice.models.Module;
import com.courseservice.models.ModuleUnlock;
import com.courseservice.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModuleProgressChecker {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ModuleRepository moduleRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final CourseEventProducer courseEventProducer;

    public record ModuleProgressResult(UUID nextModuleId, boolean courseCompleted) {
        static ModuleProgressResult notDone() { return new ModuleProgressResult(null, false); }
    }

    public ModuleProgressResult tryComplete(Module module, Enrolment enrolment) {
        UUID moduleId = module.getId();
        UUID userId   = enrolment.getUserId();

        long totalLessons  = lessonRepository.countByModuleId(moduleId);
        long doneLessons   = lessonProgressRepository.countByUserIdAndModuleId(userId, moduleId);
        long totalQuizzes  = quizRepository.countByModuleId(moduleId);
        long passedQuizzes = quizAttemptRepository.countPassedDistinctByUserIdAndModuleId(userId, moduleId);

        if (doneLessons < totalLessons || passedQuizzes < totalQuizzes) {
            return ModuleProgressResult.notDone();
        }

        UUID courseId = module.getCourse().getId();
        String now = Instant.now().toString();

        Module nextModule = moduleRepository.findByCourseIdOrderByOrderIndex(courseId).stream()
                .filter(m -> m.getOrderIndex() > module.getOrderIndex())
                .min(Comparator.comparingInt(Module::getOrderIndex))
                .orElse(null);

        if (nextModule != null) {
            ModuleUnlock unlock = new ModuleUnlock();
            unlock.setEnrolment(enrolment);
            unlock.setModule(nextModule);
            moduleUnlockRepository.save(unlock);

            courseEventProducer.emitModuleUnlocked(
                    ModuleUnlockedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .occurredAt(now)
                            .userId(userId)
                            .courseId(courseId)
                            .enrolmentId(enrolment.getId())
                            .unlockedModuleId(nextModule.getId())
                            .unlockedModuleTitle(nextModule.getTitle())
                            .unlockedModuleOrder(nextModule.getOrderIndex())
                            .build()
            );
            return new ModuleProgressResult(nextModule.getId(), false);
        }

        enrolment.setStatus(EnrolmentStatus.COMPLETED);
        enrolment.setCompletedAt(LocalDateTime.now());
        enrolmentRepository.save(enrolment);

        courseEventProducer.emitCourseCompleted(
                CourseCompletedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .occurredAt(now)
                        .userId(userId)
                        .courseId(courseId)
                        .enrolmentId(enrolment.getId())
                        .completedAt(now)
                        .build()
        );
        return new ModuleProgressResult(null, true);
    }
}
