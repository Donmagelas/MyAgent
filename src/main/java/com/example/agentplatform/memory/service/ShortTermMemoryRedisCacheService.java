package com.example.agentplatform.memory.service;

import com.example.agentplatform.config.RedisCacheProperties;
import com.example.agentplatform.memory.domain.RecentConversationMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 短期记忆滑动窗口 Redis 缓存服务。
 * 通过“会话版本号 + 最近 N 条消息”组合键，避免每轮都回源数据库读取窗口。
 */
@Service
public class ShortTermMemoryRedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemoryRedisCacheService.class);
    private static final TypeReference<List<RecentConversationMessage>> RECENT_MESSAGES_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheProperties redisCacheProperties;

    public ShortTermMemoryRedisCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisCacheProperties redisCacheProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisCacheProperties = redisCacheProperties;
    }

    /**
     * 读取当前会话的短期记忆窗口缓存。
     */
    public List<RecentConversationMessage> get(Long userId, Long conversationId, int limit) {
        if (!redisCacheProperties.enabled() || conversationId == null || limit <= 0) {
            return null;
        }
        long version = currentVersion(conversationId);
        String key = buildWindowKey(userId, conversationId, limit, version);
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(key);
            if (cachedJson == null || cachedJson.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cachedJson, RECENT_MESSAGES_TYPE);
        }
        catch (Exception exception) {
            log.warn("读取短期记忆 Redis 缓存失败: key={}, reason={}", key, exception.getMessage());
            return null;
        }
    }

    /**
     * 写入当前会话的短期记忆窗口缓存。
     */
    public void put(Long userId, Long conversationId, int limit, List<RecentConversationMessage> recentMessages) {
        if (!redisCacheProperties.enabled() || conversationId == null || limit <= 0) {
            return;
        }
        String key = buildWindowKey(userId, conversationId, limit, currentVersion(conversationId));
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(recentMessages == null ? List.of() : recentMessages),
                    redisCacheProperties.shortTermMemoryTtl()
            );
        }
        catch (Exception exception) {
            log.warn("写入短期记忆 Redis 缓存失败: key={}, reason={}", key, exception.getMessage());
        }
    }

    /**
     * 会话写入新消息后递增版本号，使旧窗口缓存自然失效。
     */
    public void markConversationUpdated(Long conversationId) {
        if (!redisCacheProperties.enabled() || conversationId == null) {
            return;
        }
        String versionKey = buildVersionKey(conversationId);
        try {
            stringRedisTemplate.opsForValue().increment(versionKey);
            Duration versionTtl = redisCacheProperties.shortTermMemoryVersionTtl();
            if (versionTtl != null && !versionTtl.isNegative() && !versionTtl.isZero()) {
                stringRedisTemplate.expire(versionKey, versionTtl);
            }
        }
        catch (Exception exception) {
            log.warn("更新短期记忆 Redis 版本号失败: key={}, reason={}", versionKey, exception.getMessage());
        }
    }

    private long currentVersion(Long conversationId) {
        String versionKey = buildVersionKey(conversationId);
        try {
            String cachedVersion = stringRedisTemplate.opsForValue().get(versionKey);
            if (cachedVersion == null || cachedVersion.isBlank()) {
                return 0L;
            }
            return Long.parseLong(cachedVersion);
        }
        catch (Exception exception) {
            log.warn("读取短期记忆 Redis 版本号失败: key={}, reason={}", versionKey, exception.getMessage());
            return 0L;
        }
    }

    private String buildVersionKey(Long conversationId) {
        return redisCacheProperties.keyPrefix() + ":memory:short-term:version:" + conversationId;
    }

    private String buildWindowKey(Long userId, Long conversationId, int limit, long version) {
        return redisCacheProperties.keyPrefix()
                + ":memory:short-term:window:"
                + (userId == null ? 0L : userId)
                + ':'
                + conversationId
                + ':'
                + limit
                + ':'
                + version;
    }
}
