package com.courseservice.services;

import com.courseservice.dto.response.CourseResponse;
import com.courseservice.events.dto.CoursePublishedEvent;
import com.courseservice.exception.CoursePublishException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.models.CourseStatus;
import com.courseservice.models.Lesson;
import com.courseservice.models.Module;
import com.courseservice.models.User;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.LessonRepository;
import com.courseservice.repositories.ModuleRepository;
import com.courseservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;

    @Transactional
    public CourseResponse publish(Long courseId, User caller) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found."));

        if (!course.getInstructorId().equals(caller.getId())) {
            throw new AccessDeniedException("You do not own this course.");
        }

        List<Module> modules = moduleRepository.findAllByCourse_IdOrderByOrderIndexAsc(courseId);
        if (modules.isEmpty()) {
            throw new CoursePublishException("Course must have at least one module to publish.");
        }

        List<CoursePublishedEvent.LessonSummary> lessonSummaries = new ArrayList<>();
        for (Module mod : modules) {
            List<Lesson> lessons = lessonRepository.findAllByModule_IdOrderByOrderIndexAsc(mod.getId());
            if (lessons.isEmpty()) {
                throw new CoursePublishException(
                        "Module '" + mod.getTitle() + "' must have at least one lesson to publish.");
            }
            for (Lesson l : lessons) {
                lessonSummaries.add(new CoursePublishedEvent.LessonSummary(
                        l.getId(), l.getTitle(), l.getDescription(),
                        l.getContentType().name(),
                        mod.getId(), mod.getTitle(), mod.getDescription()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        course.setStatus(CourseStatus.PUBLISHED);
        course.setPublishedAt(now);
        courseRepository.save(course);

        User instructor = userRepository.findById(course.getInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor not found."));

        CoursePublishedEvent event = new CoursePublishedEvent(
                UUID.randomUUID().toString(),
                "course.published",
                1,
                now.toString(),
                courseId,
                course.getTitle(),
                new CoursePublishedEvent.Instructor(instructor.getId(), instructor.getFullName()),
                lessonSummaries);

        outboxService.publish("course.published", courseId.toString(), event);

        return new CourseResponse(course);
    }
}
