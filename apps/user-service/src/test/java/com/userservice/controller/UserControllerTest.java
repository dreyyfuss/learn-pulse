package com.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.userservice.dto.response.UserProfileResponse;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.security.UserPrincipal;
import com.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:userctrltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!"
})
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserService userService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UserProfileResponse profile;

    @BeforeEach
    void setUp() {
        profile = new UserProfileResponse(
                USER_ID, "user@example.com", "Test User",
                UserStatus.ACTIVE, List.of("LEARNER"), LocalDateTime.now());
    }

    // Injects the X-User-* headers that HeaderAuthFilter reads to build the SecurityContext
    private MockHttpServletRequestBuilder asLearner(MockHttpServletRequestBuilder req) {
        return req
                .header("X-User-Id", USER_ID.toString())
                .header("X-User-Email", "user@example.com")
                .header("X-User-Roles", "LEARNER");
    }

    // --- GET /api/users/me ---

    @Test
    void getMe_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getMe_authenticated_returnsProfile() throws Exception {
        when(userService.getMe(any(UserPrincipal.class))).thenReturn(profile);

        mockMvc.perform(asLearner(get("/api/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.email", is("user@example.com")))
                .andExpect(jsonPath("$.data.fullName", is("Test User")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    void getMe_userNotFound_returns404() throws Exception {
        when(userService.getMe(any(UserPrincipal.class)))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

        mockMvc.perform(asLearner(get("/api/users/me")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    // --- PATCH /api/users/me ---

    @Test
    void updateMe_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"New Name\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updateMe_updatesFullName_returns200() throws Exception {
        UserProfileResponse updated = new UserProfileResponse(
                USER_ID, "user@example.com", "New Name",
                UserStatus.ACTIVE, List.of("LEARNER"), LocalDateTime.now());
        when(userService.updateMe(any(UserPrincipal.class), any())).thenReturn(updated);

        mockMvc.perform(asLearner(patch("/api/users/me"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName", is("New Name")));
    }

    @Test
    void updateMe_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(asLearner(patch("/api/users/me"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void updateMe_fullNameTooLong_returns400() throws Exception {
        String longName = "A".repeat(121);
        mockMvc.perform(asLearner(patch("/api/users/me"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void updateMe_emptyBody_returns200() throws Exception {
        when(userService.updateMe(any(UserPrincipal.class), any())).thenReturn(profile);

        mockMvc.perform(asLearner(patch("/api/users/me"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }
}