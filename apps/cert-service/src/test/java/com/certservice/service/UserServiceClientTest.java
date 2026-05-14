package com.certservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceClientTest {

    @Mock RestTemplate restTemplate;
    @InjectMocks UserServiceClient client;

    private static final String BASE_URL = "http://user-service:8081";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
    }

    @Test
    void getUser_success_returnsFullNameAndEmail() {
        String userId = "abc-123";
        when(restTemplate.getForObject(BASE_URL + "/internal/users/" + userId, Map.class))
                .thenReturn(Map.of("fullName", "Alice Smith", "email", "alice@example.com"));

        UserServiceClient.UserInfo result = client.getUser(userId);

        assertThat(result.fullName()).isEqualTo("Alice Smith");
        assertThat(result.email()).isEqualTo("alice@example.com");
    }

    @Test
    void getUser_nullResponseBody_returnsFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        UserServiceClient.UserInfo result = client.getUser("any-id");

        assertThat(result.fullName()).isEqualTo("Learner");
        assertThat(result.email()).isEqualTo("");
    }

    @Test
    void getUser_networkException_returnsFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        UserServiceClient.UserInfo result = client.getUser("any-id");

        assertThat(result.fullName()).isEqualTo("Learner");
        assertThat(result.email()).isEqualTo("");
    }

    @Test
    void getUser_missingFullNameKey_defaultsToLearner() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("email", "alice@example.com"));

        UserServiceClient.UserInfo result = client.getUser("any-id");

        assertThat(result.fullName()).isEqualTo("Learner");
    }

    @Test
    void getUser_missingEmailKey_defaultsToEmpty() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("fullName", "Alice"));

        UserServiceClient.UserInfo result = client.getUser("any-id");

        assertThat(result.email()).isEqualTo("");
    }

    @Test
    void getUser_buildsUrlFromBaseUrlAndUserId() {
        String userId = "user-999";
        when(restTemplate.getForObject(BASE_URL + "/internal/users/" + userId, Map.class))
                .thenReturn(Map.of("fullName", "Bob", "email", "bob@x.com"));

        UserServiceClient.UserInfo result = client.getUser(userId);

        assertThat(result.fullName()).isEqualTo("Bob");
    }
}