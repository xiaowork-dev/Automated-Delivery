package com.xiaowork.autodelivery.common;

import org.slf4j.MDC;
import java.util.List;

public final class Api {
    private Api() {}
    public record Result<T>(Object code, String message, T data, String requestId) {
        public static <T> Result<T> ok(T data) { return new Result<>(0, "success", data, MDC.get("requestId")); }
        public static Result<Void> error(String code, String message) { return new Result<>(code, message, null, MDC.get("requestId")); }
    }
    public record Page<T>(List<T> records, long total, int page, int size) {}
}
