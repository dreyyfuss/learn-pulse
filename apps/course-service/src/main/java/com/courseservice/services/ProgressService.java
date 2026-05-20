package com.courseservice.services;

import com.courseservice.dto.response.LessonProgressItemResponse;
import com.courseservice.dto.response.ModuleProgressResponse;
import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.dto.response.QuizProgressItemResponse;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Enrolment;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.models.Quiz;
import com.courseservice.models.QuizAttempt;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LessonProgressRepository;
import com.courseservice.repositories.ModuleUnlockRepository;
import com.courseservice.repositories.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final EnrolmentRepository enrolmentRepository;
    private final CourseRepository courseRepository;
    private final ModuleUnlockRepository moduleUnlockRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(UUID enrolmentId, UUID callerId, boolean isAdmin) {
        Enrolment enrolment = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrolment not found: " + enrolmentId));

        Course course = courseRepository.findWithModulesAndLessonsById(enrolment.getCourse().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        if (!isAdmin
                && !callerId.equals(enrolment.getUserId())
                && !callerId.equals(course.getInstructorId())) {
            throw new NotOwnerException("Access denied.");
        }

        Set<UUID> unlockedModuleIds = moduleUnlockRepository.findByEnrolmentId(enrolmentId)
                .stream().map(mu -> mu.getModule().getId())
                .collect(Collectors.toSet());

        List<UUID> allLessonIds = course.getModules().stream()
                .flatMap(m -> m.getLessons().stream())
                .map(Lesson::getId)
                .collect(Collectors.toList());

        Set<UUID> completedLessonIds = allLessonIds.isEmpty()
                ? Collections.emptySet()
                : lessonProgressRepository.findByUserIdAndLessonIdIn(enrolment.getUserId(), allLessonIds)
                        .stream().map(lp -> lp.getLesson().getId())
                        .collect(Collectors.toSet());

        long totalQuizzes = course.getModules().stream()
                .mapToLong(m -> m.getQuizzes().size()).sum();
        long passedQuizzes = course.getModules().stream()
                .flatMap(m -> m.getQuizzes().stream())
                .filter(q -> quizAttemptRepository.existsByQuizIdAndUserIdAndPassedTrue(q.getId(), enrolment.getUserId()))
                .count();

        long total = allLessonIds.size() + totalQuizzes;
        long done  = completedLessonIds.size() + passedQuizzes;
        int progressPercent = total == 0 ? 0 : (int) Math.round(100.0 * done / total);

        UUID currentLessonId = computeCurrentLessonId(course, unlockedModuleIds, completedLessonIds);

        List<ModuleProgressResponse> moduleResponses = course.getModules().stream()
                .sorted(Comparator.comparingInt(Module::getOrderIndex))
                .map(m -> buildModuleResponse(m, unlockedModuleIds, completedLessonIds, enrolment.getUserId()))
                .collect(Collectors.toList());

        return new ProgressResponse(
                enrolmentId,
                course.getId(),
                course.getTitle(),
                enrolment.getStatus(),
                enrolment.getEnrolledAt(),
                enrolment.getStartedAt(),
                enrolment.getCompletedAt(),
                progressPercent,
                currentLessonId,
                moduleResponses
        );
    }

    private UUID computeCurrentLessonId(Course course, Set<UUID> unlockedModuleIds, Set<UUID> completedLessonIds) {
        return course.getModules().stream()
                .filter(m -> unlockedModuleIds.contains(m.getId()))
                .sorted(Comparator.comparingInt(Module::getOrderIndex))
                .flatMap(m -> m.getLessons().stream()
                        .sorted(Comparator.comparingInt(Lesson::getOrderIndex)))
                .filter(l -> !completedLessonIds.contains(l.getId()))
                .map(Lesson::getId)
                .findFirst()
                .orElse(null);
    }

    private ModuleProgressResponse buildModuleResponse(
            Module module, Set<UUID> unlockedModuleIds, Set<UUID> completedLessonIds, UUID userId) {
        boolean unlocked = unlockedModuleIds.contains(module.getId());
        List<LessonProgressItemResponse> lessonResponses = module.getLessons().stream()
                .sorted(Comparator.comparingInt(Lesson::getOrderIndex))
                .map(l -> new LessonProgressItemResponse(
                        l.getId(), l.getTitle(), l.getOrderIndex(),
                        completedLessonIds.contains(l.getId())))
                .collect(Collectors.toList());

        List<QuizProgressItemResponse> quizResponses = module.getQuizzes().stream()
                .sorted(Comparator.comparingInt(Quiz::getOrderIndex))
                .map(q -> {
                    boolean passed = quizAttemptRepository.existsByQuizIdAndUserIdAndPassedTrue(q.getId(), userId);
                    Integer bestScore = quizAttemptRepository.findAllByQuizIdAndUserIdOrderByScoreDesc(q.getId(), userId)
                            .stream().findFirst().map(QuizAttempt::getScore).orElse(null);
                    return new QuizProgressItemResponse(
                            q.getId(), q.getTitle(), q.getOrderIndex(), passed, bestScore);
                })
                .collect(Collectors.toList());

        boolean lessonsAllDone = lessonResponses.isEmpty()
                || lessonResponses.stream().allMatch(LessonProgressItemResponse::completed);
        boolean quizzesAllPassed = quizResponses.isEmpty()
                || quizResponses.stream().allMatch(QuizProgressItemResponse::passed);
        boolean completed = !lessonResponses.isEmpty() || !quizResponses.isEmpty()
                ? lessonsAllDone && quizzesAllPassed
                : false;

        return new ModuleProgressResponse(
                module.getId(), module.getTitle(), module.getOrderIndex(),
                unlocked, completed, lessonResponses, quizResponses);
    }
}
