package com.courseservice.controllers;

import com.courseservice.dto.response.AttemptResultResponse;
import com.courseservice.dto.response.QuizPlayerResponse;
import com.courseservice.enums.QuestionType;
import com.courseservice.services.QuizAttemptService;
import com.courseservice.services.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        "spring.datasource.url=jdbc:h2:mem:quizattempttest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class QuizAttemptControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean QuizService quizService;
    @MockitoBean QuizAttemptService quizAttemptService;

    private static final UUID LEARNER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID QUIZ_ID       = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ATTEMPT_ID    = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID MODULE_ID     = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void getForPlayer_asLearner_200() throws Exception {
        when(quizService.getForPlayer(eq(QUIZ_ID), any()))
                .thenReturn(stubPlayerResponse());

        mockMvc.perform(get("/api/quizzes/{qId}/player", QUIZ_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(QUIZ_ID.toString()))
                .andExpect(jsonPath("$.data.questions").isArray());
    }

    @Test
    void getForPlayer_unauthenticated_4xx() throws Exception {
        mockMvc.perform(get("/api/quizzes/{qId}/player", QUIZ_ID))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getForPlayer_asInstructor_403() throws Exception {
        mockMvc.perform(get("/api/quizzes/{qId}/player", QUIZ_ID)
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitAttempt_asLearner_200() throws Exception {
        when(quizAttemptService.submit(eq(QUIZ_ID), any(), any()))
                .thenReturn(new AttemptResultResponse(ATTEMPT_ID, 100, true, 70, null, false, List.of()));

        mockMvc.perform(post("/api/quizzes/{qId}/attempts", QUIZ_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.passingScore").value(70));
    }

    @Test
    void submitAttempt_unauthenticated_4xx() throws Exception {
        mockMvc.perform(post("/api/quizzes/{qId}/attempts", QUIZ_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":{}}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getBestAttempt_hasAttempt_200() throws Exception {
        when(quizAttemptService.getBestAttempt(eq(QUIZ_ID), any()))
                .thenReturn(Optional.of(
                        new AttemptResultResponse(ATTEMPT_ID, 85, true, 70, null, false, List.of())));

        mockMvc.perform(get("/api/quizzes/{qId}/attempts/best", QUIZ_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(85))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    void getBestAttempt_noAttempt_200NullData() throws Exception {
        when(quizAttemptService.getBestAttempt(eq(QUIZ_ID), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/quizzes/{qId}/attempts/best", QUIZ_ID)
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // --- helpers ---

    private QuizPlayerResponse stubPlayerResponse() {
        QuizPlayerResponse.PlayerOptionResponse opt1 = new QuizPlayerResponse.PlayerOptionResponse(
                UUID.randomUUID(), "True", 0);
        QuizPlayerResponse.PlayerOptionResponse opt2 = new QuizPlayerResponse.PlayerOptionResponse(
                UUID.randomUUID(), "False", 1);
        QuizPlayerResponse.PlayerQuestionResponse question = new QuizPlayerResponse.PlayerQuestionResponse(
                UUID.randomUUID(), "Is the sky blue?", QuestionType.TRUE_FALSE, 0, List.of(opt1, opt2));
        return new QuizPlayerResponse(QUIZ_ID, MODULE_ID, "Quiz", null, 70, List.of(question));
    }
}
