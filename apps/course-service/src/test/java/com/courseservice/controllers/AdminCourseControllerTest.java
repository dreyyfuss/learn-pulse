package com.courseservice.controllers;

import com.courseservice.repositories.CourseRepository;
import com.courseservice.services.CourseService;
import com.courseservice.services.LessonService;
import com.courseservice.services.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
        "spring.datasource.url=jdbc:h2:mem:admincoursetest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class AdminCourseControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CourseRepository courseRepository;
    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final String ADMIN_ID   = "00000000-0000-0000-0000-000000000001";
    private static final String LEARNER_ID = "00000000-0000-0000-0000-000000000002";

    @Test
    void listAll_asAdmin_200() throws Exception {
        when(courseRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/admin/courses")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void listAll_withQuery_asAdmin_200() throws Exception {
        when(courseRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(courseRepository.findByTitleContainingIgnoreCase(anyString(), any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/admin/courses")
                        .param("q", "Spring")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void listAll_asLearner_403() throws Exception {
        mockMvc.perform(get("/api/admin/courses")
                        .header("X-User-Id", LEARNER_ID)
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isForbidden());
    }
}
