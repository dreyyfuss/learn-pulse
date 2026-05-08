package com.courseservice.services;

import com.courseservice.dto.request.CreateCourseRequest;
import com.courseservice.dto.request.UpdateCourseRequest;
import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.dto.response.CourseResponse;
import com.courseservice.dto.response.CreateCourseResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.exception.CourseNotPublishableException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RNG = new SecureRandom();

    private final CourseRepository courseRepository;

    @Transactional
    public CreateCourseResponse create(CreateCourseRequest req, UUID instructorId) {
        Course course = new Course();
        course.setInstructorId(instructorId);
        course.setTitle(req.title());
        course.setDescription(req.description());
        course.setThumbnailUrl(req.thumbnailUrl());
        course.setCategory(req.category());
        course.setVisibility(req.visibility());

        if (req.visibility() == CourseVisibility.PRIVATE) {
            course.setEnrolmentCode(generateEnrolmentCode());
        }

        courseRepository.save(course);
        return new CreateCourseResponse(course.getId(), course.getEnrolmentCode());
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryResponse> list(String q, String category, CourseVisibility visibility, Pageable pageable) {
        return PageResponse.from(
                courseRepository.findPublishedCourses(q, category, visibility, pageable)
                        .map(CourseSummaryResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID id) {
        Course course = courseRepository.findWithModulesAndLessonsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));
        return CourseResponse.from(course);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryResponse> listOwn(UUID instructorId, Pageable pageable) {
        return PageResponse.from(
                courseRepository.findAllByInstructorId(instructorId, pageable)
                        .map(CourseSummaryResponse::from)
        );
    }

    @Transactional
    public CourseSummaryResponse update(UUID id, UpdateCourseRequest req, UUID instructorId) {
        Course course = loadAndGuard(id, instructorId);

        if (req.title() != null)        course.setTitle(req.title());
        if (req.description() != null)  course.setDescription(req.description());
        if (req.thumbnailUrl() != null) course.setThumbnailUrl(req.thumbnailUrl());
        if (req.category() != null)     course.setCategory(req.category());
        if (req.visibility() != null) {
            if (req.visibility() == CourseVisibility.PRIVATE && course.getEnrolmentCode() == null) {
                course.setEnrolmentCode(generateEnrolmentCode());
            }
            course.setVisibility(req.visibility());
        }

        return CourseSummaryResponse.from(courseRepository.save(course));
    }

    @Transactional
    public CourseSummaryResponse publish(UUID id, UUID instructorId) {
        Course course = courseRepository.findWithModulesAndLessonsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));

        if (!course.getInstructorId().equals(instructorId)) {
            throw new NotOwnerException("You do not own this course.");
        }

        boolean hasModule = !course.getModules().isEmpty();
        boolean allModulesHaveLessons = course.getModules().stream()
                .allMatch(m -> !m.getLessons().isEmpty());

        if (!hasModule || !allModulesHaveLessons) {
            throw new CourseNotPublishableException(
                    "Course must have at least one module and each module must have at least one lesson.");
        }

        course.setStatus(CourseStatus.PUBLISHED);
        course.setPublishedAt(LocalDateTime.now());
        return CourseSummaryResponse.from(courseRepository.save(course));
    }

    @Transactional
    public void delete(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));
        courseRepository.delete(course);
    }

    Course loadAndGuard(UUID id, UUID instructorId) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));
        if (!course.getInstructorId().equals(instructorId)) {
            throw new NotOwnerException("You do not own this course.");
        }
        if (course.isLocked()) {
            throw new CourseAlreadyStartedException("Course is locked and cannot be modified.");
        }
        return course;
    }

    private static String generateEnrolmentCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
