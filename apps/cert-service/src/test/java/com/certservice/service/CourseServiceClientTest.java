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
class CourseServiceClientTest {

    @Mock RestTemplate restTemplate;
    @InjectMocks CourseServiceClient client;

    private static final String BASE_URL = "http://course-service:8080";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
    }

    @Test
    void getCourse_success_returnsTitleAndInstructorId() {
        String courseId = "course-abc";
        when(restTemplate.getForObject(BASE_URL + "/internal/courses/" + courseId, Map.class))
                .thenReturn(Map.of("title", "Java Fundamentals", "instructorId", "inst-001"));

        CourseServiceClient.CourseInfo result = client.getCourse(courseId);

        assertThat(result.title()).isEqualTo("Java Fundamentals");
        assertThat(result.instructorId()).isEqualTo("inst-001");
    }

    @Test
    void getCourse_nullResponseBody_returnsFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        CourseServiceClient.CourseInfo result = client.getCourse("any-id");

        assertThat(result.title()).isEqualTo("Course");
        assertThat(result.instructorId()).isEqualTo("");
    }

    @Test
    void getCourse_networkException_returnsFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        CourseServiceClient.CourseInfo result = client.getCourse("any-id");

        assertThat(result.title()).isEqualTo("Course");
        assertThat(result.instructorId()).isEqualTo("");
    }

    @Test
    void getCourse_missingTitleKey_defaultsToCourse() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("instructorId", "inst-001"));

        CourseServiceClient.CourseInfo result = client.getCourse("any-id");

        assertThat(result.title()).isEqualTo("Course");
    }

    @Test
    void getCourse_missingInstructorIdKey_defaultsToEmpty() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("title", "Design Basics"));

        CourseServiceClient.CourseInfo result = client.getCourse("any-id");

        assertThat(result.instructorId()).isEqualTo("");
    }

    @Test
    void getCourse_buildsUrlFromBaseUrlAndCourseId() {
        String courseId = "course-999";
        when(restTemplate.getForObject(BASE_URL + "/internal/courses/" + courseId, Map.class))
                .thenReturn(Map.of("title", "Spring Boot", "instructorId", ""));

        CourseServiceClient.CourseInfo result = client.getCourse(courseId);

        assertThat(result.title()).isEqualTo("Spring Boot");
    }
}