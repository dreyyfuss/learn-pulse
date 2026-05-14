package com.userservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String status,
        T data,
        String message,
        ErrorDetail error
) {

    public record ErrorDetail(String code, Object details) {}

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("success", data, message, null);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>("success", null, message, null);
    }

    public static ApiResponse<Void> error(String message, String code) {
        return new ApiResponse<>("error", null, message, new ErrorDetail(code, null));
    }

    public static ApiResponse<Void> error(String message, String code, Object details) {
        return new ApiResponse<>("error", null, message, new ErrorDetail(code, details));
    }
}
