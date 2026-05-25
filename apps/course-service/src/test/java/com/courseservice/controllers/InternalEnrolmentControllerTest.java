package com.courseservice.controllers;

import com.courseservice.repositories.EnrolmentRepository;
import com.courseservice.services.CourseService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:internalenroltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class InternalEnrolmentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean EnrolmentRepository enrolmentRepository;
    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void checkEnrolment_asService_200() throws Exception {
        mockMvc.perform(get("/internal/courses/{courseId}/enrolment", COURSE_ID)
                        .param("userId", USER_ID.toString())
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000099")
                        .header("X-User-Roles", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false));
    }
}
