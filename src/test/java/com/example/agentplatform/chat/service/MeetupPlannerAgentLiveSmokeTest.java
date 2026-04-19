package com.example.agentplatform.chat.service;

import com.example.agentplatform.agent.domain.AgentReasoningMode;
import com.example.agentplatform.auth.domain.AuthenticatedUserPrincipal;
import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.auth.dto.AuthLoginRequest;
import com.example.agentplatform.auth.dto.AuthLoginResponse;
import com.example.agentplatform.auth.service.AuthService;
import com.example.agentplatform.chat.dto.ChatAskRequest;
import com.example.agentplatform.chat.dto.ChatStreamEvent;
import com.example.agentplatform.tools.service.MeetupRecommendationToolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚会地点推荐主链路 live smoke。
 * 默认不运行；需要验证模型路由、skill 注入、工具暴露和流式事件时传入 -Dlive.agent=true。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "live.agent", matches = "true")
class MeetupPlannerAgentLiveSmokeTest {

    @Autowired
    private SecuredChatFacade securedChatFacade;

    @Autowired
    private AuthService authService;

    @Test
    void shouldRouteMeetupQuestionAndCallMeetupTool() {
        Authentication authentication = loginAsUser();
        ChatAskRequest request = new ChatAskRequest(
                "live-meetup-" + UUID.randomUUID(),
                "我们两个人想在苏州喝咖啡，我在苏州大学独墅湖校区，小王在苏州工业园区湖东邻里中心，推荐一个大家通勤都公平的地方。",
                AgentReasoningMode.LOOP,
                8,
                false,
                null
        );

        List<ChatStreamEvent> events = securedChatFacade.smartStream(request, authentication)
                .map(ServerSentEvent::data)
                .filter(event -> event != null)
                .collectList()
                .block(Duration.ofSeconds(90));

        assertThat(events).isNotNull().isNotEmpty();
        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("skill");
            assertThat(event.metadata()).containsEntry("skillId", "meetup-planner");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("observation");
            assertThat(event.metadata()).containsEntry("toolName", MeetupRecommendationToolService.TOOL_NAME);
        });
        assertThat(events).anySatisfy(event -> assertThat(event.type()).isEqualTo("done"));
    }

    private Authentication loginAsUser() {
        AuthLoginResponse response = authService.login(new AuthLoginRequest("user", "123456"));
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                response.userId(),
                response.username(),
                "",
                List.of(new SimpleGrantedAuthority(SecurityRole.authority(SecurityRole.CHAT_USER)))
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                response.accessToken(),
                principal.getAuthorities()
        );
    }
}
