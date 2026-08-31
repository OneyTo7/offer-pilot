package com.eyki.offerpilot.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResult<T> success() {
        return new ApiResult<>(200, "success", null, null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, "success", data, null);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(200, message, data, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResult<T> error(int code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.code = code;
        result.message = message;
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResult<T> error(int code, String message, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }

    public ApiResult<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}