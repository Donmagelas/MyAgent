package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import org.springframework.core.Ordered;

/**
 * 聊天请求前置检查的最小 advisor 抽象。
 * 用于集中执行模型调用前的可复用守卫逻辑。
 */
public interface ChatAdvisor extends Ordered {

    /** 返回稳定的 advisor 名称，便于日志与调试。 */
    String getName();

    /** 针对当前请求上下文执行 advisor。 */
    void before(ChatAdvisorContext context);

    @Override
    default int getOrder() {
        return 0;
    }
}
