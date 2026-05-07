package com.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks BlacklistService blacklistService;

    private UUID userId;
    private String key;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        key = "blacklist:user:" + userId;
    }

    // --- add ---

    @Test
    void add_setsKeyWithSevenDayTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        blacklistService.add(userId);
        verify(valueOps).set(key, "1", Duration.ofDays(7));
    }

    @Test
    void add_usesCorrectKeyPrefix() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID otherId = UUID.randomUUID();
        blacklistService.add(otherId);
        verify(valueOps).set("blacklist:user:" + otherId, "1", Duration.ofDays(7));
    }

    // --- isBlacklisted ---

    @Test
    void isBlacklisted_keyExists_returnsTrue() {
        when(redisTemplate.hasKey(key)).thenReturn(true);
        assertThat(blacklistService.isBlacklisted(userId)).isTrue();
    }

    @Test
    void isBlacklisted_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey(key)).thenReturn(false);
        assertThat(blacklistService.isBlacklisted(userId)).isFalse();
    }

    @Test
    void isBlacklisted_nullResponseFromRedis_returnsFalse() {
        // Boolean.TRUE.equals(null) is false — suspended check must be null-safe
        when(redisTemplate.hasKey(key)).thenReturn(null);
        assertThat(blacklistService.isBlacklisted(userId)).isFalse();
    }

    // --- remove ---

    @Test
    void remove_deletesKey() {
        blacklistService.remove(userId);
        verify(redisTemplate).delete(key);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void remove_doesNotCheckExistence_beforeDelete() {
        blacklistService.remove(userId);
        verify(redisTemplate, never()).hasKey(key);
    }
}
