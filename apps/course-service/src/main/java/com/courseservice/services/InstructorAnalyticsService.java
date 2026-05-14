package com.courseservice.services;

import com.courseservice.dto.response.CourseAnalyticsResponse;
import com.courseservice.dto.response.CourseAnalyticsResponse.Aggregate;
import com.courseservice.dto.response.CourseAnalyticsResponse.LearnerStat;
import com.courseservice.enums.EnrolmentStatus;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.repositories.LearnerProgressProjection;
import com.courseservice.repositories.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstructorAnalyticsService {

    private final CourseRepository courseRepository;
    private final EnrolmentRepository enrolmentRepository;
    private final LessonRepository lessonRepository;

    @Cacheable(cacheNames = "analytics:instructor", key = "#courseId.toString() + ':' + #instructorId.toString()")
    @Transactional(readOnly = true)
    public CourseAnalyticsResponse getAnalytics(UUID courseId, UUID instructorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));

        if (!course.getInstructorId().equals(instructorId)) {
            throw new NotOwnerException("You do not own this course.");
        }

        long totalLessons    = lessonRepository.countByCourseId(courseId);
        long totalEnrolments = enrolmentRepository.countByCourseId(courseId);
        long completed       = enrolmentRepository.countByCourseIdAndStatus(courseId, EnrolmentStatus.COMPLETED);
        long active          = totalEnrolments - completed;
        double completionRate = totalEnrolments == 0 ? 0.0
                : Math.round(completed * 10000.0 / totalEnrolments) / 100.0;

        List<LearnerProgressProjection> rows =
                enrolmentRepository.findLearnerProgressByCourseId(courseId);

        List<LearnerStat> learners = rows.stream()
                .map(r -> {
                    double pct = totalLessons == 0 ? 0.0
                            : Math.round(r.getLessonsCompleted() * 10000.0 / totalLessons) / 100.0;
                    return new LearnerStat(
                            r.getUserId(),
                            r.getStatus(),
                            r.getEnrolledAt(),
                            r.getCompletedAt(),
                            r.getLessonsCompleted(),
                            pct
                    );
                })
                .toList();

        return new CourseAnalyticsResponse(
                courseId,
                new Aggregate(totalEnrolments, completed, completionRate, active),
                learners
        );
    }
}
