package com.userservice.controllers;

import com.userservice.enums.Role;
import com.userservice.exception.AppException;
import com.userservice.service.BlacklistService;
import com.userservice.service.JwtService;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Called by Traefik's ForwardAuth middleware on every protected request.
 * Validates the JWT and returns X-User-* headers that Traefik injects into
 * the upstream request. Returns 401 on invalid / missing tokens so Traefik
 * rejects the request before it reaches any downstream service.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth Validation", description = "Internal token validation endpoint")
public class ValidateController {

    private final JwtService jwtService;
    private final BlacklistService blacklistService;

    @Operation(summary = "Validate JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token valid with user info"),
        @ApiResponse(responseCode = "401", description = "Token invalid or expired")
    })
    @GetMapping("/validate")
    public ResponseEntity<Void> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Missing or malformed Authorization header.");
        }

        String token = authHeader.substring(7);

        try {
            if (jwtService.isRefreshToken(token)) {
                throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Refresh tokens are not accepted here.");
            }

            UUID userId  = jwtService.extractUserId(token);

            if (blacklistService.isBlacklisted(userId)) {
                throw new AppException(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "User account is suspended.");
            }

            String email = jwtService.extractEmail(token);
            Set<Role> roles = jwtService.extractRoles(token);
            String rolesHeader = roles.stream().map(Role::name).collect(Collectors.joining(","));

            return ResponseEntity.ok()
                    .header("X-User-Id",    String.valueOf(userId))
                    .header("X-User-Email", email)
                    .header("X-User-Roles", rolesHeader)
                    .build();

        } catch (AppException ex) {
            throw ex;
        } catch (JwtException ex) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token is invalid or expired.");
        }
    }
}
