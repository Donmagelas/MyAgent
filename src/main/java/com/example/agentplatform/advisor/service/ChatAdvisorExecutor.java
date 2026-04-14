package com.example.agentplatform.advisor.service;

import com.example.agentplatform.advisor.domain.ChatAdvisorContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按顺序执行所有已注册的自定义 advisor。
 * 这样可以把权限和策略校验从 controller 和聊天编排逻辑中解耦出来。
 */
@Service
public class ChatAdvisorExecutor {

    private final List<ChatAdvisor> advisors;

    public ChatAdvisorExecutor(List<ChatAdvisor> advisors) {
        this.advisors = new ArrayList<>(advisors);
        AnnotationAwareOrderComparator.sort(this.advisors);
    }

    /** 在聊天流程继续前依次执行所有 advisor。 */
    public void execute(ChatAdvisorContext context) {
        for (ChatAdvisor advisor : advisors) {
            advisor.before(context);
        }
    }
}
