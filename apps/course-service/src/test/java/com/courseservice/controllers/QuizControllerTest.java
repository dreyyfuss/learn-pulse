package com.courseservice.controllers;

import com.courseservice.dto.response.QuizDetailResponse;
import com.courseservice.enums.QuestionType;
import com.courseservice.exception.CourseAlreadyStartedException;
import com.courseservice.exception.NotOwnerException;
import com.courseservice.services.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
        "spring.datasource.url=jdbc:h2:mem:quiztest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class QuizControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean QuizService quizService;

    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEARNER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MODULE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID QUIZ_ID       = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void createQuiz_asInstructor_201() throws Exception {
        when(quizService.create(any(), any(), any(), any()))
                .thenReturn(stubDetail("Quiz 1", 70));

        mockMvc.perform(post("/api/courses/{cId}/modules/{mId}/quizzes", COURSE_ID, MODULE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Quiz 1","orderIndex":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$.data.title").value("Quiz 1"));
    }

    @Test
    void createQuiz_unauthenticated_4xx() throws Exception {
        mockMvc.perform(post("/api/courses/{cId}/modules/{mId}/quizzes", COURSE_ID, MODULE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Quiz 1","orderIndex":0}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createQuiz_asLearner_403() throws Exception {
        mockMvc.perform(post("/api/courses/{cId}/modules/{mId}/quizzes", COURSE_ID, MODULE_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Quiz 1","orderIndex":0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getQuiz_asInstructor_200() throws Exception {
        when(quizService.getForInstructor(any(), any(), any(), any()))
                .thenReturn(stubDetailWithQuestion());

        mockMvc.perform(get("/api/courses/{cId}/modules/{mId}/quizzes/{qId}",
                        COURSE_ID, MODULE_ID, QUIZ_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$.data.questions").isArray());
    }

    @Test
    void updateQuiz_asInstructor_200() throws Exception {
        when(quizService.update(any(), any(), any(), any(), any()))
                .thenReturn(stubDetail("Updated Quiz", 80));

        mockMvc.perform(patch("/api/courses/{cId}/modules/{mId}/quizzes/{qId}",
                        COURSE_ID, MODULE_ID, QUIZ_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated Quiz","passingScore":80}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Quiz"))
                .andExpect(jsonPath("$.data.passingScore").value(80));
    }

    @Test
    void deleteQuiz_asInstructor_204() throws Exception {
        mockMvc.perform(delete("/api/courses/{cId}/modules/{mId}/quizzes/{qId}",
                        COURSE_ID, MODULE_ID, QUIZ_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isNoContent());
    }

    @Test
    void upsertQuestions_asInstructor_200() throws Exception {
        when(quizService.upsertQuestions(any(), any(), any(), any(), any()))
                .thenReturn(stubDetailWithQuestion());

        mockMvc.perform(put("/api/courses/{cId}/modules/{mId}/quizzes/{qId}/questions",
                        COURSE_ID, MODULE_ID, QUIZ_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questions":[
                                  {"questionText":"Q1","questionType":"MCQ","options":[
                                    {"optionText":"A","isCorrect":true},
                                    {"optionText":"B","isCorrect":false}
                                  ]}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(1));
    }

    @Test
    void reorderQuizzes_asInstructor_204() throws Exception {
        UUID QUIZ_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000006");

        mockMvc.perform(put("/api/courses/{cId}/modules/{mId}/quizzes/reorder",
                        COURSE_ID, MODULE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quizzes":[
                                  {"id":"%s","orderIndex":0},
                                  {"id":"%s","orderIndex":1}
                                ]}
                                """.formatted(QUIZ_ID, QUIZ_ID_2)))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorderQuizzes_asLearner_403() throws Exception {
        mockMvc.perform(put("/api/courses/{cId}/modules/{mId}/quizzes/reorder",
                        COURSE_ID, MODULE_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quizzes":[]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createQuiz_lockedCourse_409() throws Exception {
        when(quizService.create(any(), any(), any(), any()))
                .thenThrow(new CourseAlreadyStartedException("Course is locked."));

        mockMvc.perform(post("/api/courses/{cId}/modules/{mId}/quizzes", COURSE_ID, MODULE_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Quiz","orderIndex":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE_LOCKED"));
    }

    // --- helpers ---

    private QuizDetailResponse stubDetail(String title, int passingScore) {
        return new QuizDetailResponse(QUIZ_ID, MODULE_ID, title, null, 0, passingScore,
                LocalDateTime.now(), List.of());
    }

    private QuizDetailResponse stubDetailWithQuestion() {
        QuizDetailResponse.OptionResponse opt1 = new QuizDetailResponse.OptionResponse(
                UUID.randomUUID(), "True", true, 0);
        QuizDetailResponse.OptionResponse opt2 = new QuizDetailResponse.OptionResponse(
                UUID.randomUUID(), "False", false, 1);
        QuizDetailResponse.QuestionResponse question = new QuizDetailResponse.QuestionResponse(
                UUID.randomUUID(), "Is the sky blue?", QuestionType.TRUE_FALSE, 0, List.of(opt1, opt2));
        return new QuizDetailResponse(QUIZ_ID, MODULE_ID, "Quiz", null, 0, 70,
                LocalDateTime.now(), List.of(question));
    }
}
