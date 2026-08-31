package com.eyki.offerpilot.common.exception;

import com.eyki.offerpilot.common.model.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, message);
    }

    public static BusinessException resumeNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
    }

    public static BusinessException positionNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "目标职位不存在");
    }

    public static BusinessException reportNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "评估报告不存在");
    }

    public static BusinessException interviewNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "面试会话不存在");
    }

    public static BusinessException interviewExpired() {
        return new BusinessException(ErrorCode.INTERVIEW_EXPIRED, "面试已过期，请重新开始");
    }

    public static BusinessException interviewClosed() {
        return new BusinessException(ErrorCode.INTERVIEW_CLOSED, "面试已结束，无法继续操作");
    }

    public static BusinessException emailAlreadyRegistered() {
        return new BusinessException(ErrorCode.CONFLICT, "该邮箱已被注册");
    }

    public static BusinessException aiServiceError(String message) {
        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务异常: " + message);
    }
}