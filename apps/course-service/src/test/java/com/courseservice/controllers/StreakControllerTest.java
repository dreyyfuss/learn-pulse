package com.courseservice.controllers;

import com.courseservice.dto.response.StreakResponse;
import com.courseservice.services.StreakService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
        "spring.datasource.url=jdbc:h2:mem:streaktest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.cache.type=none"
})
class StreakControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean StreakService streakService;

    private static final UUID LEARNER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void getStreak_asLearner_200WithStreakData() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(streakService.getStreak(any())).thenReturn(new StreakResponse(5, today));

        mockMvc.perform(get("/api/learner/streak")
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.currentStreak").value(5))
                .andExpect(jsonPath("$.data.lastActivityDate").value(today.toString()));
    }

    @Test
    void getStreak_noActivity_200WithZeroStreak() throws Exception {
        when(streakService.getStreak(any())).thenReturn(new StreakResponse(0, null));

        mockMvc.perform(get("/api/learner/streak")
                        .header("X-User-Id", LEARNER_ID.toString())
                        .header("X-User-Roles", "LEARNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.lastActivityDate").doesNotExist());
    }

    @Test
    void getStreak_unauthenticated_4xx() throws Exception {
        mockMvc.perform(get("/api/learner/streak"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getStreak_asInstructor_403() throws Exception {
        mockMvc.perform(get("/api/learner/streak")
                        .header("X-User-Id", INSTRUCTOR_ID.toString())
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isForbidden());
    }
}
