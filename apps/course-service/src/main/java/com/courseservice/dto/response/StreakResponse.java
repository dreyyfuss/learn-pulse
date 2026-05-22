package com.courseservice.dto.response;

import java.time.LocalDate;

public record StreakResponse(int currentStreak, LocalDate lastActivityDate) {}
