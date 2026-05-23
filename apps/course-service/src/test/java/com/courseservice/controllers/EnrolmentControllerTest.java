package com.courseservice.controllers;

import com.courseservice.services.CourseService;
import com.courseservice.services.EnrolmentService;
import com.courseservice.services.LessonService;
import com.courseservice.services.ModuleService;
import com.courseservice.services.ProgressService;
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
        "spring.datasource.url=jdbc:h2:mem:enroltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class EnrolmentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean EnrolmentService enrolmentService;
    @MockitoBean ProgressService progressService;
    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final String LEARNER_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID   ENROL_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID   COURSE_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void enrol_unauthenticated_403() throws Exception {
        mockMvc.perform(post("/api/enrolments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + COURSE_ID + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enrol_asLearner_201() throws Exception {
        mockMvc.perform(post("/api/enrolments")
                        .header("X-User-Id", LEARNER_ID)
                        .header("X-User-Roles", "LEARNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + COURSE_ID + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void start_asLearner_200() throws Exception {
        mockMvc.perform(post("/api/enrolments/{id}/start", ENROL_ID)
                        .header("X-User-Id", LEARNER_ID)
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk());
    }

    @Test
    void progress_asLearner_200() throws Exception {
        mockMvc.perform(get("/api/enrolments/{id}/progress", ENROL_ID)
                        .header("X-User-Id", LEARNER_ID)
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk());
    }

    @Test
    void progress_asAdmin_200() throws Exception {
        mockMvc.perform(get("/api/enrolments/{id}/progress", ENROL_ID)
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000099")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }
}
