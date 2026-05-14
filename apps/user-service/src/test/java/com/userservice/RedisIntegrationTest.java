package com.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:redistest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long!!"
})
class RedisIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Test
    void writeAndReadKey() {
        redisTemplate.opsForValue().set("test:hello", "world");
        String value = redisTemplate.opsForValue().get("test:hello");
        assertThat(value).isEqualTo("world");
    }

    @Test
    void keyWithTtlExists() {
        redisTemplate.opsForValue().set("test:ttl-key", "1", Duration.ofMinutes(5));
        assertThat(redisTemplate.hasKey("test:ttl-key")).isTrue();
    }

    @Test
    void deletedKeyIsAbsent() {
        redisTemplate.opsForValue().set("test:delete-me", "value");
        redisTemplate.delete("test:delete-me");
        assertThat(redisTemplate.hasKey("test:delete-me")).isFalse();
    }
}
