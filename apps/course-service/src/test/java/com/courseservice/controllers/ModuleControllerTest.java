package com.courseservice.controllers;

import com.courseservice.services.CourseService;
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
        "spring.datasource.url=jdbc:h2:mem:moduletest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class ModuleControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ModuleService moduleService;
    @MockitoBean CourseService courseService;
    @MockitoBean LessonService lessonService;

    private static final String INSTRUCTOR_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID   COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID   MODULE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void createModule_asInstructor_201() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/modules", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Module 1\",\"orderIndex\":0}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createModule_asLearner_403() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/modules", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "LEARNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Module 1\",\"orderIndex\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reorderModules_asInstructor_204() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/modules/reorder", COURSE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleIds\":[\"" + MODULE_ID + "\"]}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateModule_asInstructor_200() throws Exception {
        mockMvc.perform(patch("/api/courses/{courseId}/modules/{id}", COURSE_ID, MODULE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Module\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteModule_asInstructor_204() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/modules/{id}", COURSE_ID, MODULE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isNoContent());
    }
}
