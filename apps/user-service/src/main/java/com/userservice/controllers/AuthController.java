package com.userservice.controllers;

import com.userservice.dto.request.LoginRequest;
import com.userservice.dto.request.RefreshRequest;
import com.userservice.dto.request.RegisterRequest;
import com.userservice.dto.response.ApiResponse;
import com.userservice.dto.response.LoginResponse;
import com.userservice.dto.response.RegisterResponse;
import com.userservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest req) {
        RegisterResponse data = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Account created."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse data = authService.login(req);
        return ResponseEntity.ok(ApiResponse.success(data, "Login successful."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        LoginResponse data = authService.refresh(req);
        return ResponseEntity.ok(ApiResponse.success(data, "Token refreshed."));
    }
}
