package com.courseservice.services;

import com.courseservice.dto.response.LessonProgressItemResponse;
import com.courseservice.dto.response.ModuleProgressResponse;
import com.courseservice.dto.response.ProgressResponse;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.Enrolment;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LessonProgressRepository;
import com.courseservice.repositories.ModuleUnlockRepository;
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

        int total = allLessonIds.size();
        int progressPercent = total == 0 ? 0 : (int) Math.round(100.0 * completedLessonIds.size() / total);

        UUID currentLessonId = computeCurrentLessonId(course, unlockedModuleIds, completedLessonIds);

        List<ModuleProgressResponse> moduleResponses = course.getModules().stream()
                .sorted(Comparator.comparingInt(Module::getOrderIndex))
                .map(m -> buildModuleResponse(m, unlockedModuleIds, completedLessonIds))
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
            Module module, Set<UUID> unlockedModuleIds, Set<UUID> completedLessonIds) {
        boolean unlocked = unlockedModuleIds.contains(module.getId());
        List<LessonProgressItemResponse> lessonResponses = module.getLessons().stream()
                .sorted(Comparator.comparingInt(Lesson::getOrderIndex))
                .map(l -> new LessonProgressItemResponse(
                        l.getId(), l.getTitle(), l.getOrderIndex(),
                        completedLessonIds.contains(l.getId())))
                .collect(Collectors.toList());

        boolean completed = !lessonResponses.isEmpty()
                && lessonResponses.stream().allMatch(LessonProgressItemResponse::completed);

        return new ModuleProgressResponse(
                module.getId(), module.getTitle(), module.getOrderIndex(),
                unlocked, completed, lessonResponses);
    }
}
