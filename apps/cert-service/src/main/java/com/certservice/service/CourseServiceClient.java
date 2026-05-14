package com.certservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.course-service-url:http://course-service:8080}")
    private String baseUrl;

    public record CourseInfo(String title, String instructorId) {}

    @SuppressWarnings("unchecked")
    public CourseInfo getCourse(String courseId) {
        try {
            String url = baseUrl + "/internal/courses/" + courseId;
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            if (body == null) throw new RuntimeException("Empty response from course-service");
            return new CourseInfo(
                    (String) body.getOrDefault("title", "Course"),
                    (String) body.getOrDefault("instructorId", "")
            );
        } catch (Exception e) {
            log.error("Failed to fetch course {}: {}", courseId, e.getMessage());
            return new CourseInfo("Course", "");
        }
    }
}
