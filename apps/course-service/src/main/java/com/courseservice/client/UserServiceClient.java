package com.courseservice.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    public Map<String, String> getNames(Collection<UUID> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        String joined = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        String url = userServiceUrl + "/internal/users/batch?ids=" + joined;
        try {
            ResponseEntity<Map<String, String>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return resp.getBody() != null ? resp.getBody() : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}