package com.courseservice.controllers;

import com.courseservice.models.Course;
import com.courseservice.repositories.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
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
        "spring.datasource.url=jdbc:h2:mem:internalcoursetest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class InternalCourseControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  CourseRepository courseRepository;

    private static final UUID COURSE_ID     = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setTitle("Advanced Spring Boot");
        course.setInstructorId(INSTRUCTOR_ID);
    }

    @Test
    void getCourse_exists_returnsTitleAndInstructorId() throws Exception {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

        mockMvc.perform(get("/internal/courses/" + COURSE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Advanced Spring Boot")))
                .andExpect(jsonPath("$.instructorId", is(INSTRUCTOR_ID.toString())));
    }

    @Test
    void getCourse_notFound_returns404() throws Exception {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/courses/" + COURSE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCourse_noAuthRequired_accessibleWithoutHeaders() throws Exception {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

        // No X-User-* headers — /internal/** is permit-all
        mockMvc.perform(get("/internal/courses/" + COURSE_ID))
                .andExpect(status().isOk());
    }
}
