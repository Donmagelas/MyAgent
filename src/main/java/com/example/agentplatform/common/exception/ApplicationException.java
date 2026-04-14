package com.example.agentplatform.common.exception;

/**
 * 项目级业务异常。
 * 用于封装可预期失败，避免内部异常细节直接外泄。
 */
public class ApplicationException extends RuntimeException {

    public ApplicationException(String message) {
        super(message);
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
