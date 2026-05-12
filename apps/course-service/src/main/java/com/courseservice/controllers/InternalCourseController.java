package com.courseservice.controllers;

import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCourseController {

    private final CourseRepository courseRepository;

    @GetMapping("/courses/{id}")
    public ResponseEntity<Map<String, String>> getCourse(@PathVariable UUID id) {
        return courseRepository.findById(id)
                .map(course -> ResponseEntity.ok(Map.of(
                        "title", course.getTitle(),
                        "instructorId", course.getInstructorId().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
