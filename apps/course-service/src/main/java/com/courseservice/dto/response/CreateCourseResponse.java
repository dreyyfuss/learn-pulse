package com.courseservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateCourseResponse(UUID courseId, String enrolmentCode) {}
