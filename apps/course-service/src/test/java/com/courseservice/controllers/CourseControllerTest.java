package com.courseservice.controllers;

import com.courseservice.dto.response.CourseSummaryResponse;
import com.courseservice.dto.response.CreateCourseResponse;
import com.courseservice.dto.response.PageResponse;
import com.courseservice.enums.CourseStatus;
import com.courseservice.enums.CourseVisibility;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.services.CourseService;
import com.courseservice.services.LessonService;
import com.courseservice.services.ModuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:coursetest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class CourseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void createCourse_asInstructor_201() throws Exception {
        when(courseService.create(any(), eq(INSTRUCTOR_ID)))
                .thenReturn(new CreateCourseResponse(COURSE_ID, null));

        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Spring Boot 101","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.courseId").value(COURSE_ID.toString()));
    }

    @Test
    void createCourse_unauthenticated_4xx() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Spring Boot 101","visibility":"PUBLIC"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listCourses_200() throws Exception {
        CourseSummaryResponse summary = new CourseSummaryResponse(
                COURSE_ID, INSTRUCTOR_ID, "Spring Boot 101", null, null, null,
                CourseVisibility.PUBLIC, CourseStatus.PUBLISHED, false, null, LocalDateTime.now(), LocalDateTime.now());

        when(courseService.list(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/courses")
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(COURSE_ID.toString()));
    }

    @Test
    void deleteCourse_asAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/courses/{id}", COURSE_ID)
                        .header("X-User-Id", ADMIN_ID.toString())
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void publishCourse_notOwner_403() throws Exception {
        doThrow(new NotOwnerException("You do not own this course."))
                .when(courseService).publish(eq(COURSE_ID), any());

        mockMvc.perform(post("/api/courses/{id}/publish", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_OWNER"));
    }

    @Test
    void updateCourse_asNonOwner_403() throws Exception {
        UUID otherInstructor = UUID.fromString("f9e8d7c6-0000-0000-0000-000000000002");
        when(courseService.update(eq(COURSE_ID), any(), any()))
                .thenThrow(new NotOwnerException("You do not own this course."));

        mockMvc.perform(patch("/api/courses/{id}", COURSE_ID)
                        .header("X-User-Id", otherInstructor.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated Title"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_OWNER"));
    }

    @Test
    void publishCourse_alreadyPublished_409() throws Exception {
        doThrow(new CourseAlreadyStartedException("Course is already published."))
                .when(courseService).publish(eq(COURSE_ID), any());

        mockMvc.perform(post("/api/courses/{id}/publish", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isConflict());
    }

    @Test
    void getCourse_notFound_404() throws Exception {
        when(courseService.get(eq(COURSE_ID)))
                .thenThrow(new ResourceNotFoundException("Course not found"));

        mockMvc.perform(get("/api/courses/{id}", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCourse_asLearner_403() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "LEARNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Spring Boot 101","visibility":"PUBLIC"}
                                """))
                .andExpect(status().is4xxClientError());
    }
}