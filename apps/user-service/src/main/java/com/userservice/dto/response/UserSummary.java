package com.userservice.dto.response;

import java.util.List;
import java.util.UUID;

public record UserSummary(UUID id, String fullName, List<String> roles) {}
