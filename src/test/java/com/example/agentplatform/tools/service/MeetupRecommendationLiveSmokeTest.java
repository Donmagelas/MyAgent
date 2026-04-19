package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.config.AmapProperties;
import com.example.agentplatform.tools.client.AmapClient;
import com.example.agentplatform.tools.client.AmapWebClient;
import com.example.agentplatform.tools.domain.PermissionContext;
import com.example.agentplatform.tools.domain.ToolContextKeys;
import com.example.agentplatform.tools.dto.MeetupParticipant;
import com.example.agentplatform.tools.dto.MeetupRecommendationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 聚会地点推荐工具 live smoke。
 * 默认不运行，避免普通单测依赖外部网络；需要真实验证时传入 -Dlive.amap=true。
 */
@EnabledIfSystemProperty(named = "live.amap", matches = "true")
class MeetupRecommendationLiveSmokeTest {

    @Test
    void shouldRecommendMeetupPlaceWithLiveAmapApi() {
        String apiKey = readLocalAmapApiKey();
        assumeTrue(StringUtils.hasText(apiKey), "本地配置中未找到高德 API key，跳过 live smoke");

        AmapProperties amapProperties = new AmapProperties(
                true,
                "https://restapi.amap.com",
                apiKey,
                Duration.ofSeconds(10),
                1,
                Duration.ofMillis(300),
                Duration.ofMillis(400),
                "苏州",
                5_000,
                15_000,
                3,
                5,
                "driving",
                8,
                20,
                2,
                new AmapProperties.Score(0.45d, 0.30d, 0.20d, 0.05d)
        );
        AmapClient amapClient = new AmapWebClient(WebClient.builder(), new ObjectMapper(), amapProperties);
        MeetupRecommendationToolService service = new MeetupRecommendationToolService(
                amapClient,
                amapProperties,
                new ToolPermissionGuard()
        );

        MeetupRecommendationResult result = service.recommendMeetupPlace(
                "苏州",
                List.of(
                        new MeetupParticipant("用户", "苏州大学独墅湖校区"),
                        new MeetupParticipant("小王", "苏州工业园区湖东邻里中心")
                ),
                "咖啡",
                "driving",
                2,
                5_000,
                liveToolContext()
        );

        assertThat(result.recommendations()).isNotEmpty();
        assertThat(result.recommendations().get(0).routeCosts())
                .anySatisfy(routeCost -> assertThat(routeCost.durationMinutes()).isPositive());
    }

    private ToolContext liveToolContext() {
        PermissionContext permissionContext = new PermissionContext(
                1L,
                "live-smoke",
                Set.of(SecurityRole.CHAT_USER),
                Set.of(MeetupRecommendationToolService.TOOL_NAME),
                Set.of(),
                Set.of(),
                false
        );
        return new ToolContext(Map.of(ToolContextKeys.PERMISSION_CONTEXT, permissionContext));
    }

    private String readLocalAmapApiKey() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application-local.yml"));
        Properties properties = yaml.getObject();
        if (properties == null) {
            return "";
        }
        return properties.getProperty("app.tools.amap.api-key", "");
    }
}
