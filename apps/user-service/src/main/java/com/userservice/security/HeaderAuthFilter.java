package com.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads X-User-Id / X-User-Email / X-User-Roles headers injected by Traefik's ForwardAuth
 * middleware after a successful /api/auth/validate call, then populates the SecurityContext.
 * Used by all three backend services — no JWT dependency required downstream.
 */
public class HeaderAuthFilter extends OncePerRequestFilter {

    static final String HEADER_USER_ID    = "X-User-Id";
    static final String HEADER_USER_EMAIL = "X-User-Email";
    static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);

        if (userId != null && !userId.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String rolesHeader = request.getHeader(HEADER_USER_ROLES);
            List<SimpleGrantedAuthority> authorities = rolesHeader != null && !rolesHeader.isBlank()
                    ? Arrays.stream(rolesHeader.split(","))
                              .map(String::trim)
                              .filter(r -> !r.isEmpty())
                              .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                              .collect(Collectors.toList())
                    : Collections.emptyList();

            String email = request.getHeader(HEADER_USER_EMAIL);
            UserPrincipal principal = new UserPrincipal(UUID.fromString(userId), email);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
