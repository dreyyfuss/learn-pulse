package com.courseservice.services;

import com.courseservice.config.JwtConfig;
import com.courseservice.dto.request.UpdateProfileRequest;
import com.courseservice.dto.response.UserResponse;
import com.courseservice.exception.ResourceNotFoundException;
import com.courseservice.models.Role;
import com.courseservice.models.User;
import com.courseservice.models.UserStatus;
import com.courseservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtConfig jwtConfig;

    public UserResponse getProfile(Long userId) {
        User user = findOrThrow(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findOrThrow(userId);
        user.setFullName(request.fullName());
        return UserResponse.from(userRepository.save(user));
    }

    // Admin operations

    public Page<UserResponse> listAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse promoteToAdmin(Long userId) {
        User user = findOrThrow(userId);
        user.getRoles().add(Role.ADMIN);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse suspendUser(Long userId) {
        User user = findOrThrow(userId);
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        // Write blacklist entry so existing JWT tokens are immediately rejected.
        // TTL = refresh token lifetime (longest token a suspended user could hold).
        long ttlSeconds = jwtConfig.getRefreshTokenExpiryMs() / 1000;
        redisTemplate.opsForValue().set("blacklist:user:" + userId, "1", ttlSeconds, TimeUnit.SECONDS);

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse reinstateUser(Long userId) {
        User user = findOrThrow(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Remove the blacklist entry so the user can log in again
        redisTemplate.delete("blacklist:user:" + userId);

        return UserResponse.from(user);
    }

    private User findOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
