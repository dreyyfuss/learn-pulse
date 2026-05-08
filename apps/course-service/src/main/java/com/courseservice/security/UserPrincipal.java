package com.courseservice.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class UserPrincipal {
    private final UUID id;
    private final String email;
}
