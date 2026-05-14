package com.userservice.repository;

import com.userservice.domain.user.User;
import com.userservice.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndLoadRoundTrip() {
        User user = new User();
        user.setEmail("ada@example.com");
        user.setPasswordHash("$2a$12$hashedpassword");
        user.setFullName("Ada Lovelace");
        user.addRole(Role.LEARNER);

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEmail()).isEqualTo("ada@example.com");
        assertThat(found.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(found.getRoles()).containsExactly(Role.LEARNER);
    }

    @Test
    void findByEmail_returnsUser() {
        User user = new User();
        user.setEmail("bob@example.com");
        user.setPasswordHash("$2a$12$hashedpassword");
        user.setFullName("Bob");
        userRepository.save(user);

        assertThat(userRepository.findByEmail("bob@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
    }
}
