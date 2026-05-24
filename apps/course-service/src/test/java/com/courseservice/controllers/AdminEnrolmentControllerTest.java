package com.courseservice.controllers;

import com.courseservice.services.CourseService;
import com.courseservice.services.EnrolmentService;
import com.courseservice.services.LessonService;
import com.courseservice.services.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
        "spring.datasource.url=jdbc:h2:mem:adminenroltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class AdminEnrolmentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean EnrolmentService enrolmentService;
    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final String ADMIN_ID      = "00000000-0000-0000-0000-000000000001";
    private static final String INSTRUCTOR_ID = "00000000-0000-0000-0000-000000000002";
    private static final UUID   ENROL_ID      = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID   COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void listAll_asAdmin_200() throws Exception {
        when(enrolmentService.listAllEnrolments(any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/admin/enrolments")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void listAll_asInstructor_403() throws Exception {
        mockMvc.perform(get("/api/admin/enrolments")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEnrol_asAdmin_201() throws Exception {
        mockMvc.perform(post("/api/admin/enrolments")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + INSTRUCTOR_ID + "\",\"courseId\":\"" + COURSE_ID + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminUnenrol_asAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/admin/enrolments/{id}", ENROL_ID)
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNoContent());
    }
}
