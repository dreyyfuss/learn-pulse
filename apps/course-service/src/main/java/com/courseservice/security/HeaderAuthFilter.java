package com.courseservice.security;

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
import java.util.stream.Collectors;

/**
 * Reads X-User-Id / X-User-Email / X-User-Roles headers injected by Traefik's ForwardAuth
 * middleware and populates the Spring SecurityContext. No JWT dependency in this service.
 */
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");

        if (userId != null && !userId.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String rolesHeader = request.getHeader("X-User-Roles");
            List<SimpleGrantedAuthority> authorities = rolesHeader != null && !rolesHeader.isBlank()
                    ? Arrays.stream(rolesHeader.split(","))
                              .map(String::trim)
                              .filter(r -> !r.isEmpty())
                              .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                              .collect(Collectors.toList())
                    : Collections.emptyList();

            String email = request.getHeader("X-User-Email");
            UserPrincipal principal = new UserPrincipal(Long.parseLong(userId), email);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        }

        filterChain.doFilter(request, response);
    }
}
