package com.userservice.controller;

import com.userservice.dto.response.UserAdminView;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:adminctrltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!"
})
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AdminUserService adminUserService;

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TARGET_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private UserAdminView activeView;
    private UserAdminView suspendedView;
    private UserAdminView promotedView;

    @BeforeEach
    void setUp() {
        activeView    = new UserAdminView(TARGET_USER_ID, "user@example.com", "Test User", UserStatus.ACTIVE,    List.of("LEARNER"),        null);
        suspendedView = new UserAdminView(TARGET_USER_ID, "user@example.com", "Test User", UserStatus.SUSPENDED, List.of("LEARNER"),        null);
        promotedView  = new UserAdminView(TARGET_USER_ID, "user@example.com", "Test User", UserStatus.ACTIVE,    List.of("LEARNER", "ADMIN"), null);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder req) {
        return req
                .header("X-User-Id",    ADMIN_ID.toString())
                .header("X-User-Email", "admin@example.com")
                .header("X-User-Roles", "ADMIN");
    }

    private MockHttpServletRequestBuilder asLearner(MockHttpServletRequestBuilder req) {
        return req
                .header("X-User-Id",    UUID.randomUUID().toString())
                .header("X-User-Email", "learner@example.com")
                .header("X-User-Roles", "LEARNER");
    }

    // --- GET /api/admin/users ---

    @Test
    void listUsers_noAuth_returns4xx() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listUsers_nonAdminRole_returns403() throws Exception {
        mockMvc.perform(asLearner(get("/api/admin/users")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_adminRole_returnsPage() throws Exception {
        when(adminUserService.listUsers(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(activeView)));

        mockMvc.perform(asAdmin(get("/api/admin/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].email", is("user@example.com")));
    }

    @Test
    void listUsers_emptyResult_returns200WithEmptyPage() throws Exception {
        when(adminUserService.listUsers(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asAdmin(get("/api/admin/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(0)));
    }

    // --- PATCH /api/admin/users/{id}/promote ---

    @Test
    void promote_adminRole_addsAdminRoleToUser() throws Exception {
        when(adminUserService.promote(TARGET_USER_ID)).thenReturn(promotedView);

        mockMvc.perform(asAdmin(patch("/api/admin/users/" + TARGET_USER_ID + "/promote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("ADMIN")));
    }

    @Test
    void promote_nonAdminCaller_returns403() throws Exception {
        mockMvc.perform(asLearner(patch("/api/admin/users/" + TARGET_USER_ID + "/promote")))
                .andExpect(status().isForbidden());
    }

    @Test
    void promote_userNotFound_returns404() throws Exception {
        when(adminUserService.promote(TARGET_USER_ID))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

        mockMvc.perform(asAdmin(patch("/api/admin/users/" + TARGET_USER_ID + "/promote")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    // --- PATCH /api/admin/users/{id}/suspend ---

    @Test
    void suspend_adminRole_setsStatusSuspended() throws Exception {
        when(adminUserService.suspend(TARGET_USER_ID)).thenReturn(suspendedView);

        mockMvc.perform(asAdmin(patch("/api/admin/users/" + TARGET_USER_ID + "/suspend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUSPENDED")));
    }

    @Test
    void suspend_userNotFound_returns404() throws Exception {
        when(adminUserService.suspend(TARGET_USER_ID))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

        mockMvc.perform(asAdmin(patch("/api/admin/users/" + TARGET_USER_ID + "/suspend")))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/admin/users/{id}/reinstate ---

    @Test
    void reinstate_adminRole_setsStatusActive() throws Exception {
        when(adminUserService.reinstate(TARGET_USER_ID)).thenReturn(activeView);

        mockMvc.perform(asAdmin(patch("/api/admin/users/" + TARGET_USER_ID + "/reinstate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    void reinstate_userNotFound_returns404() throws Exception {
        when(adminUserService.reinstate(TARGET_USER_ID))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

        mockMvc.perform(asAdmin(patch("/api/admin/users/" + TARGET_USER_ID + "/reinstate")))
                .andExpect(status().isNotFound());
    }
}
