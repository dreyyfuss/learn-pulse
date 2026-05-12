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
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.user-service-url:http://user-service:8081}")
    private String baseUrl;

    public record UserInfo(String fullName, String email) {}

    @SuppressWarnings("unchecked")
    public UserInfo getUser(String userId) {
        try {
            String url = baseUrl + "/internal/users/" + userId;
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            if (body == null) throw new RuntimeException("Empty response from user-service");
            return new UserInfo(
                    (String) body.getOrDefault("fullName", "Learner"),
                    (String) body.getOrDefault("email", "")
            );
        } catch (Exception e) {
            log.error("Failed to fetch user {}: {}", userId, e.getMessage());
            return new UserInfo("Learner", "");
        }
    }
}
