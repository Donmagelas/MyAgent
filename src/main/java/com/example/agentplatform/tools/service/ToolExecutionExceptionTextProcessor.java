package com.example.agentplatform.tools.service;

import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 工具执行异常处理器。
 * 负责把工具执行异常转换成可传回工具链的简洁文本。
 */
@Component
public class ToolExecutionExceptionTextProcessor implements ToolExecutionExceptionProcessor {

    /**
     * 权限拒绝标记。
     * ToolChatService 会识别这个前缀，并直接向外返回 403。
     */
    public static final String PERMISSION_DENIED_PREFIX = "__PERMISSION_DENIED__:";

    @Override
    public String process(ToolExecutionException exception) {
        if (isPermissionDenied(exception)) {
            return PERMISSION_DENIED_PREFIX + resolveMessage(exception);
        }
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return "工具执行失败";
        }
        return "工具执行失败：" + message;
    }

    private boolean isPermissionDenied(ToolExecutionException exception) {
        if (exception == null) {
            return false;
        }
        if (exception.getCause() instanceof AccessDeniedException) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.contains("AccessDeniedException");
    }

    private String resolveMessage(ToolExecutionException exception) {
        if (exception == null) {
            return "工具执行被拒绝";
        }
        if (exception.getCause() instanceof AccessDeniedException accessDeniedException
                && accessDeniedException.getMessage() != null
                && !accessDeniedException.getMessage().isBlank()) {
            return accessDeniedException.getMessage();
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "工具执行被拒绝";
        }
        return message;
    }
}