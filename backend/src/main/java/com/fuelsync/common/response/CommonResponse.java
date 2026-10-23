package com.fuelsync.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String errorCode;
    private final LocalDateTime timestamp;

    private CommonResponse(boolean success, String message, T data, String errorCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> CommonResponse<T> ok(T data) {
        return new CommonResponse<>(true, "OK", data, null);
    }

    public static <T> CommonResponse<T> ok(String message, T data) {
        return new CommonResponse<>(true, message, data, null);
    }

    public static <T> CommonResponse<T> fail(String errorCode, String message) {
        return new CommonResponse<>(false, message, null, errorCode);
    }
}
