package com.eyki.offerpilot.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.common.model.ErrorCode;
import com.eyki.offerpilot.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ApiResult.error(e.getCode(), e.getMessage()).withTraceId(TraceIdUtil.getTraceId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<?> handleValidation(MethodArgumentNotValidException e) {
        String msg =
            e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ApiResult.error(ErrorCode.BAD_REQUEST, msg).withTraceId(TraceIdUtil.getTraceId());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<?> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return ApiResult.error(ErrorCode.BAD_REQUEST, e.getMessage()).withTraceId(TraceIdUtil.getTraceId());
    }

    @ExceptionHandler(NotLoginException.class)
    public ApiResult<?> handleNotLogin(NotLoginException e) {
        log.warn("未登录: {}", e.getMessage());
        return ApiResult.error(ErrorCode.UNAUTHORIZED, "未登录或登录已过期，请重新登录")
            .withTraceId(TraceIdUtil.getTraceId());
    }

    @ExceptionHandler(NotPermissionException.class)
    public ApiResult<?> handleNotPermission(NotPermissionException e) {
        log.warn("无权限: {}", e.getMessage());
        return ApiResult.error(ErrorCode.FORBIDDEN, "无权限访问").withTraceId(TraceIdUtil.getTraceId());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<?> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return ApiResult.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试")
            .withTraceId(TraceIdUtil.getTraceId());
    }
}