package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.RedisCacheProperties;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.example.agentplatform.memory.repository.ShortTermMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短期记忆 Redis 缓存 live smoke。
 * 依赖本地 Redis，默认不运行；需要真实验证时传入 -Dlive.redis=true。
 */
@EnabledIfSystemProperty(named = "live.redis", matches = "true")
class ShortTermMemoryRedisCacheLiveTest {

    private LettuceConnectionFactory lettuceConnectionFactory;
    private StringRedisTemplate stringRedisTemplate;
    private String keyPrefix;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = loadRedisConfiguration();
        lettuceConnectionFactory = new LettuceConnectionFactory(configuration);
        lettuceConnectionFactory.afterPropertiesSet();

        stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(lettuceConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        keyPrefix = "short-term-cache-live-test:" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (stringRedisTemplate != null) {
            Set<String> keys = stringRedisTemplate.keys(keyPrefix + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
        if (lettuceConnectionFactory != null) {
            lettuceConnectionFactory.destroy();
        }
    }

    @Test
    void shouldHitRedisCacheAndInvalidateByConversationVersion() {
        ShortTermMemoryRepository shortTermMemoryRepository = mock(ShortTermMemoryRepository.class);
        ShortTermMemoryRedisCacheService cacheService = new ShortTermMemoryRedisCacheService(
                stringRedisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new RedisCacheProperties(true, keyPrefix, Duration.ofMinutes(10), Duration.ofMinutes(20))
        );
        DefaultShortTermMemoryService shortTermMemoryService =
                new DefaultShortTermMemoryService(shortTermMemoryRepository, cacheService);

        List<RecentConversationMessage> firstWindow = List.of(
                message(101L, 2001L, 3001L, "user", "第一条消息")
        );
        List<RecentConversationMessage> secondWindow = List.of(
                message(102L, 2001L, 3001L, "assistant", "第二条消息")
        );
        when(shortTermMemoryRepository.findRecentMessages(3001L, 2001L, 8))
                .thenReturn(firstWindow)
                .thenReturn(secondWindow);

        List<RecentConversationMessage> firstLoad =
                shortTermMemoryService.loadRecentMessages(3001L, 2001L, 8);
        List<RecentConversationMessage> secondLoad =
                shortTermMemoryService.loadRecentMessages(3001L, 2001L, 8);

        assertThat(firstLoad).extracting(RecentConversationMessage::content)
                .containsExactly("第一条消息");
        assertThat(secondLoad).extracting(RecentConversationMessage::content)
                .containsExactly("第一条消息");
        verify(shortTermMemoryRepository, times(1)).findRecentMessages(3001L, 2001L, 8);

        shortTermMemoryService.markConversationUpdated(2001L);
        String versionKey = keyPrefix + ":memory:short-term:version:2001";
        Long expireSeconds = stringRedisTemplate.getExpire(versionKey, TimeUnit.SECONDS);
        assertThat(expireSeconds).isNotNull();
        assertThat(expireSeconds).isPositive();
        List<RecentConversationMessage> thirdLoad =
                shortTermMemoryService.loadRecentMessages(3001L, 2001L, 8);

        assertThat(thirdLoad).extracting(RecentConversationMessage::content)
                .containsExactly("第二条消息");
        verify(shortTermMemoryRepository, times(2)).findRecentMessages(3001L, 2001L, 8);
    }

    /**
     * 读取 application-local.yml 中的 Redis 连接配置。
     * 如果本地配置缺失，则回退到 localhost:6379。
     */
    private RedisStandaloneConfiguration loadRedisConfiguration() {
        YamlPropertiesFactoryBean yamlFactoryBean = new YamlPropertiesFactoryBean();
        yamlFactoryBean.setResources(new FileSystemResource("src/main/resources/application-local.yml"));
        Properties properties = yamlFactoryBean.getObject();

        String host = readProperty(properties, "spring.data.redis.host", "localhost");
        int port = Integer.parseInt(readProperty(properties, "spring.data.redis.port", "6379"));
        String password = readProperty(properties, "spring.data.redis.password", "");
        int database = Integer.parseInt(readProperty(properties, "spring.data.redis.database", "0"));

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setDatabase(database);
        if (!password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        return configuration;
    }

    private String readProperty(Properties properties, String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private RecentConversationMessage message(
            Long messageId,
            Long conversationId,
            Long userId,
            String role,
            String content
    ) {
        return new RecentConversationMessage(
                messageId,
                conversationId,
                userId,
                role,
                content,
                "TEXT",
                null,
                OffsetDateTime.now()
        );
    }
}
