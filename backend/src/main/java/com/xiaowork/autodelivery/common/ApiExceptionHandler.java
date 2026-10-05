package com.xiaowork.autodelivery.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<Api.Result<Void>> routeMissing(Exception ex) { return ResponseEntity.status(404).body(Api.Result.error("NOT_FOUND", "接口不存在")); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Api.Result<Void>> methodUnsupported(Exception ex) { return ResponseEntity.status(405).body(Api.Result.error("METHOD_NOT_ALLOWED", "此接口不支持该请求方法")); }
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Api.Result<Void>> business(BusinessException ex) { return ResponseEntity.status(ex.status).body(Api.Result.error(ex.code, ex.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Api.Result<Void>> validation(Exception ex) { return ResponseEntity.badRequest().body(Api.Result.error("INVALID_ARGUMENT", "请求参数不合法，请检查输入")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Api.Result<Void>> duplicate(Exception ex) { return ResponseEntity.status(409).body(Api.Result.error("DATA_CONFLICT", "数据已存在或状态发生变化")); }
    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<Api.Result<Void>> busy(Exception ex) { return ResponseEntity.status(409).body(Api.Result.error("RESOURCE_BUSY", "资源正在处理，请重试")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Api.Result<Void>> unexpected(Exception ex) {
        // Do not serialize request bodies, tokens, passwords or codes into logs.
        log.error("Unexpected API failure requestId={} type={} stack={}", org.slf4j.MDC.get("requestId"), ex.getClass().getName(), java.util.Arrays.toString(ex.getStackTrace()));
        return ResponseEntity.internalServerError().body(Api.Result.error("INTERNAL_ERROR", "服务暂时异常，请稍后重试"));
    }
}
