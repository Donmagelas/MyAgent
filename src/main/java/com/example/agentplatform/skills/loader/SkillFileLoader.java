package com.example.agentplatform.skills.loader;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.SkillProperties;
import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.domain.SkillToolChoiceMode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 文件加载器。
 * 从 classpath 或外部目录读取 skill.yaml 和 prompt.md，并转换为统一的 SkillDefinition。
 */
@Component
public class SkillFileLoader {

    private final SkillProperties skillProperties;
    private final PathMatchingResourcePatternResolver resourcePatternResolver;

    public SkillFileLoader(SkillProperties skillProperties) {
        this.skillProperties = skillProperties;
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    /**
     * 加载全部 skill 定义。
     */
    public List<SkillDefinition> loadAll() {
        if (!skillProperties.enabled()) {
            return List.of();
        }
        try {
            Resource[] resources = resourcePatternResolver.getResources(skillProperties.locationPattern());
            List<SkillDefinition> skills = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                skills.add(loadSingle(resource));
            }
            return skills.stream()
                    .sorted(Comparator.comparing(SkillDefinition::id))
                    .toList();
        }
        catch (IOException exception) {
            throw new ApplicationException("加载 skill 文件失败", exception);
        }
    }

    private SkillDefinition loadSingle(Resource metadataResource) {
        Map<String, Object> metadata = readYaml(metadataResource);
        Resource promptResource = resolvePromptResource(metadataResource);
        String promptContent = readText(promptResource);

        String id = readRequiredString(metadata, "id");
        String name = readRequiredString(metadata, "name");
        String description = readString(metadata, "description", "");
        boolean enabled = readBoolean(metadata, "enabled", true);
        List<String> tags = readStringList(metadata, "tags");
        List<String> routeKeywords = readStringList(metadata, "route_keywords");
        List<String> allowedTools = readStringList(metadata, "allowed_tools");
        List<String> examples = readStringList(metadata, "examples");
        SkillToolChoiceMode toolChoiceMode = readEnum(
                metadata,
                "tool_choice_mode",
                SkillToolChoiceMode.ALLOWED
        );

        return new SkillDefinition(
                id,
                name,
                description,
                enabled,
                tags,
                routeKeywords,
                allowedTools,
                toolChoiceMode,
                examples,
                promptContent,
                describeResource(metadataResource)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            Object loaded = new Yaml().load(inputStream);
            if (loaded == null) {
                return Map.of();
            }
            if (loaded instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return normalized;
            }
            throw new ApplicationException("skill 元数据格式非法: " + describeResource(resource));
        }
        catch (IOException exception) {
            throw new ApplicationException("读取 skill 元数据失败: " + describeResource(resource), exception);
        }
    }

    private Resource resolvePromptResource(Resource metadataResource) {
        try {
            return metadataResource.createRelative("prompt.md");
        }
        catch (IOException exception) {
            throw new ApplicationException("定位 skill prompt 文件失败: " + describeResource(metadataResource), exception);
        }
    }

    private String readText(Resource resource) {
        if (resource == null || !resource.exists()) {
            return "";
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new ApplicationException("读取 skill prompt 失败: " + describeResource(resource), exception);
        }
    }

    private String readRequiredString(Map<String, Object> metadata, String key) {
        String value = readString(metadata, key, null);
        if (!StringUtils.hasText(value)) {
            throw new ApplicationException("skill 缺少必填字段: " + key);
        }
        return value;
    }

    private String readString(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private boolean readBoolean(Map<String, Object> metadata, String key, boolean defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> readStringList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of(String.valueOf(value).trim());
    }

    private <T extends Enum<T>> T readEnum(Map<String, Object> metadata, String key, T defaultValue) {
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<T> enumType = (Class<T>) defaultValue.getDeclaringClass();
            return Enum.valueOf(enumType, text.toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            throw new ApplicationException("skill 枚举字段非法: " + key + "=" + text, exception);
        }
    }

    private String describeResource(Resource resource) {
        try {
            return resource.getURL().toString();
        }
        catch (IOException exception) {
            return resource.getDescription();
        }
    }
}
