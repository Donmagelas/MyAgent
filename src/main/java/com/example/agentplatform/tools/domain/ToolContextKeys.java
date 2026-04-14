package com.example.agentplatform.tools.domain;

/**
 * ToolContext 上下文字段常量。
 * 用于在应用内部传递权限上下文、当前用户和会话信息，不暴露给模型。
 */
public final class ToolContextKeys {

    public static final String PERMISSION_CONTEXT = "permissionContext";
    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String SESSION_ID = "sessionId";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String ROOT_TASK_ID = "rootTaskId";
    public static final String STEP_INDEX = "stepIndex";

    private ToolContextKeys() {
    }
}
