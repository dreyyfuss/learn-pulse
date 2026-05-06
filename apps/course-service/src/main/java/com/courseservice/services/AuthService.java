package com.courseservice.services;

import com.courseservice.dto.request.LoginRequest;
import com.courseservice.dto.request.RegisterRequest;
import com.courseservice.dto.response.AuthResponse;
import com.courseservice.dto.response.UserResponse;
import com.courseservice.exception.AccountSuspendedException;
import com.courseservice.exception.DuplicateEmailException;
import com.courseservice.exception.InvalidTokenException;
import com.courseservice.models.Role;
import com.courseservice.models.User;
import com.courseservice.models.UserStatus;
import com.courseservice.repositories.UserRepository;
import com.courseservice.security.JwtUtil;
import com.courseservice.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new DuplicateEmailException(request.email());
        }

        Set<Role> roles = new HashSet<>();
        roles.add(Role.LEARNER);
        if (request.registerAsInstructor()) {
            roles.add(Role.INSTRUCTOR);
        }

        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException("Your account has been suspended. Contact support.");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired.");
        }
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Provided token is not a refresh token.");
        }

        String userIdStr = jwtUtil.extractUserId(refreshToken);
        User user = (User) userDetailsService.loadUserById(Long.parseLong(userIdStr));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException("Your account has been suspended.");
        }

        // Issue a new access token; reuse the same refresh token
        String newAccessToken = jwtUtil.generateAccessToken(user);
        return new AuthResponse(newAccessToken, refreshToken, UserResponse.from(user));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }
}
