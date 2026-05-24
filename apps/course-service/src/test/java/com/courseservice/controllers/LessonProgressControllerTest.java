package com.courseservice.controllers;

import com.courseservice.services.CourseService;
import com.courseservice.services.LessonProgressService;
import com.courseservice.services.LessonService;
import com.courseservice.services.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:lessonprogresstest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class LessonProgressControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean LessonProgressService lessonProgressService;
    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final String LEARNER_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID   LESSON_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void completeLesson_unauthenticated_403() throws Exception {
        mockMvc.perform(post("/api/lessons/{id}/complete", LESSON_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeLesson_asLearner_200() throws Exception {
        mockMvc.perform(post("/api/lessons/{id}/complete", LESSON_ID)
                        .header("X-User-Id", LEARNER_ID)
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk());
    }
}
