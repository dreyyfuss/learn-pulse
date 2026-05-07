package com.userservice.service;

import com.userservice.domain.user.User;
import com.userservice.dto.request.LoginRequest;
import com.userservice.dto.request.RefreshRequest;
import com.userservice.dto.request.RegisterRequest;
import com.userservice.dto.response.LoginResponse;
import com.userservice.dto.response.RegisterResponse;
import com.userservice.dto.response.UserSummary;
import com.userservice.enums.Role;
import com.userservice.enums.UserStatus;
import com.userservice.exception.AppException;
import com.userservice.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));

        // LEARNER is always granted; INSTRUCTOR added on request
        user.addRole(Role.LEARNER);
        if (req.registerAsInstructor()) {
            user.addRole(Role.INSTRUCTOR);
        }

        User saved = userRepository.save(user);

        List<String> roles = saved.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toList());

        return new RegisterResponse(saved.getId(), roles);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String email = req.email().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password."));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account is suspended.");
        }

        return buildLoginResponse(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(RefreshRequest req) {
        try {
            if (!jwtService.isRefreshToken(req.refreshToken())) {
                throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Not a refresh token.");
            }

            UUID userId = jwtService.extractUserId(req.refreshToken());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(
                            HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "User not found."));

            if (user.getStatus() == UserStatus.SUSPENDED) {
                throw new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account is suspended.");
            }

            return buildLoginResponse(user);

        } catch (JwtException ex) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token is invalid or expired.");
        }
    }

    private LoginResponse buildLoginResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toList());

        return new LoginResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                new UserSummary(user.getId(), user.getFullName(), roles)
        );
    }
}
