package com.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private static final String PREFIX = "blacklist:user:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    public void add(UUID userId) {
        redisTemplate.opsForValue().set(PREFIX + userId, "1", TTL);
    }

    public boolean isBlacklisted(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + userId));
    }

    public void remove(UUID userId) {
        redisTemplate.delete(PREFIX + userId);
    }
}
