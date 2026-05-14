package com.userservice.controller;

import com.userservice.domain.user.User;
import com.userservice.repository.UserRepository;
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
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:internalusertest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!"
})
class InternalUserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  UserRepository userRepository;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("instructor@example.com");
        user.setFullName("Bob Instructor");
    }

    @Test
    void getUser_exists_returnsFullNameAndEmail() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/internal/users/" + USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Bob Instructor")))
                .andExpect(jsonPath("$.email", is("instructor@example.com")));
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/users/" + USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUser_noAuthRequired_returns200WithoutHeaders() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // No X-User-* headers — internal endpoints are permit-all
        mockMvc.perform(get("/internal/users/" + USER_ID))
                .andExpect(status().isOk());
    }
}
