package com.eyki.offerpilot.common.model;

public interface ErrorCode {

    int SUCCESS = 200;
    int BAD_REQUEST = 400;
    int UNAUTHORIZED = 401;
    int FORBIDDEN = 403;
    int NOT_FOUND = 404;
    int CONFLICT = 409;
    int TOO_MANY_REQUESTS = 429;
    int INTERNAL_ERROR = 500;

    // 自定义业务错误码
    int AI_SERVICE_ERROR = 1001;
    int DOCUMENT_PARSE_ERROR = 1002;
    int RESUME_PARSE_TIMEOUT = 1003;
    int API_KEY_INSUFFICIENT_BALANCE = 1004;
    int INTERVIEW_EXPIRED = 2001;
    int INTERVIEW_CLOSED = 2002;
}