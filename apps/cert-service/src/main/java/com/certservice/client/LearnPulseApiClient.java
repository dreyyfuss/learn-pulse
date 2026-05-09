package com.certservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin HTTP client that fetches user and course details from their respective services.
 * Used by CertificateConsumer to populate the PDF template.
 */
@Component
@Slf4j
public class LearnPulseApiClient {

    private final RestClient userClient;
    private final RestClient courseClient;

    public LearnPulseApiClient(
            RestClient.Builder builder,
            @Value("${app.services.user-service-url:http://localhost:8081}") String userServiceUrl,
            @Value("${app.services.course-service-url:http://localhost:8080}") String courseServiceUrl) {
        this.userClient   = builder.baseUrl(userServiceUrl).build();
        this.courseClient = builder.baseUrl(courseServiceUrl).build();
    }

    public String getUserFullName(Long userId) {
        UserSummary user = userClient.get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .body(UserSummary.class);
        return user != null ? user.fullName() : "Unknown Learner";
    }

    public CourseSummary getCourse(Long courseId) {
        CourseSummary course = courseClient.get()
                .uri("/api/courses/{id}", courseId)
                .retrieve()
                .body(CourseSummary.class);
        return course != null ? course : new CourseSummary(courseId, "Unknown Course", null);
    }

    // ── Nested response projections ───────────────────────────────────────────

    public record UserSummary(Long id, String email, String fullName) {}

    public record CourseSummary(Long id, String title, Long instructorId) {}
}
