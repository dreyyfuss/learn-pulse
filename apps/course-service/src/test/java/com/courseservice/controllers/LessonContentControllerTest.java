package com.courseservice.controllers;

import com.courseservice.services.CourseService;
import com.courseservice.services.LessonContentService;
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
        "spring.datasource.url=jdbc:h2:mem:lessoncontenttest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class LessonContentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean LessonContentService lessonContentService;
    @MockitoBean CourseService courseService;
    @MockitoBean ModuleService moduleService;
    @MockitoBean LessonService lessonService;

    private static final String INSTRUCTOR_ID  = "00000000-0000-0000-0000-000000000001";
    private static final UUID   COURSE_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID   MODULE_ID      = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID   LESSON_ID      = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID   ATTACHMENT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private String basePath() {
        return "/api/courses/" + COURSE_ID + "/modules/" + MODULE_ID + "/lessons/" + LESSON_ID;
    }

    @Test
    void getContentUploadUrl_asInstructor_200() throws Exception {
        mockMvc.perform(post(basePath() + "/content/upload-url")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mimeType\":\"video/mp4\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmContentUpload_asInstructor_204() throws Exception {
        mockMvc.perform(post(basePath() + "/content/confirm")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"content/video.mp4\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getContent_200() throws Exception {
        mockMvc.perform(get(basePath() + "/content")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteContent_asInstructor_204() throws Exception {
        mockMvc.perform(delete(basePath() + "/content")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getAttachmentUploadUrl_asInstructor_200() throws Exception {
        mockMvc.perform(post(basePath() + "/attachments/upload-url")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"doc.pdf\",\"mimeType\":\"application/pdf\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmAttachmentUpload_asInstructor_201() throws Exception {
        mockMvc.perform(post(basePath() + "/attachments/confirm")
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"attachments/doc.pdf\",\"fileName\":\"doc.pdf\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getAttachmentDownloadUrl_200() throws Exception {
        mockMvc.perform(get(basePath() + "/attachments/{id}/download-url", ATTACHMENT_ID)
                        .header("X-User-Id", INSTRUCTOR_ID)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk());
    }
}
