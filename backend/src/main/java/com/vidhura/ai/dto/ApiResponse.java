package com.vidhura.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private Integer count;
    private String message;
    private String error;
    private String status;
    private String service;
    private String version;
    private String timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, int count) {
        return ApiResponse.<T>builder().success(true).data(data).count(count).build();
    }

    public static <T> ApiResponse<T> okMessage(String message) {
        return ApiResponse.<T>builder().success(true).message(message).build();
    }

    public static <T> ApiResponse<T> err(String error) {
        return ApiResponse.<T>builder().success(false).error(error).build();
    }

    public static <T> ApiResponse<T> err(String error, String message) {
        return ApiResponse.<T>builder().success(false).error(error).message(message).build();
    }
}
