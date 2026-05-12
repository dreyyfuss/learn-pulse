package com.courseservice.controllers;

import com.courseservice.models.Enrolment;
import com.courseservice.repositories.EnrolmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/courses")
@RequiredArgsConstructor
public class InternalEnrolmentController {

    private final EnrolmentRepository enrolmentRepository;

    @GetMapping("/{courseId}/enrolment")
    @PreAuthorize("hasRole('SERVICE')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> checkEnrolment(
            @PathVariable UUID courseId,
            @RequestParam UUID userId) {

        Optional<Enrolment> enrolment = enrolmentRepository.findByUserIdAndCourseId(userId, courseId);

        boolean enrolled = enrolment.isPresent();
        boolean started = enrolled && enrolment.get().getStartedAt() != null;
        String courseTitle = enrolled ? enrolment.get().getCourse().getTitle() : null;

        return ResponseEntity.ok(Map.of(
                "enrolled", enrolled,
                "started", started,
                "courseTitle", courseTitle != null ? courseTitle : ""
        ));
    }
}
