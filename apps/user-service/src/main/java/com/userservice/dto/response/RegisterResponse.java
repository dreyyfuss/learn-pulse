package com.userservice.dto.response;

import java.util.List;
import java.util.UUID;

public record RegisterResponse(UUID userId, List<String> roles) {}
