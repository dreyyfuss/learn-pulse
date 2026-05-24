package com.courseservice.controllers;

import com.courseservice.services.CourseGenerationService;
import com.courseservice.services.CourseService;
import com.courseservice.services.InstructorAnalyticsService;
import com.courseservice.services.LessonService;
import com.courseservice.services.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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
        "spring.datasource.url=jdbc:h2:mem:instructortest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class InstructorControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CourseService courseService;
    @MockitoBean InstructorAnalyticsService instructorAnalyticsService;
    @MockitoBean CourseGenerationService courseGenerationService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final String INSTRUCTOR_ID = "00000000-0000-0000-0000-000000000001";
    private static final String LEARNER_ID    = "00000000-0000-0000-0000-000000000002";
    private static final UUID   COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID   JOB_ID        = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void listOwnCourses_asInstructor_200() throws Exception {
        mockMvc.perform(get("/api/instructor/courses")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void listOwnCourses_asLearner_403() throws Exception {
        mockMvc.perform(get("/api/instructor/courses")
                        .header("X-User-Id", LEARNER_ID)
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAnalytics_asInstructor_200() throws Exception {
        mockMvc.perform(get("/api/instructor/courses/{id}/analytics", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void generateCourse_asInstructor_202() throws Exception {
        mockMvc.perform(post("/api/instructor/courses/generate")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Generate a comprehensive Java programming course\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getGenerationJob_asInstructor_200() throws Exception {
        mockMvc.perform(get("/api/instructor/courses/generate/{jobId}", JOB_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk());
    }
}
