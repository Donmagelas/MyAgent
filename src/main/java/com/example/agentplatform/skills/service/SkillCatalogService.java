package com.example.agentplatform.skills.service;

import com.example.agentplatform.skills.domain.SkillDefinition;
import com.example.agentplatform.skills.loader.SkillFileLoader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Skill 目录服务。
 * 提供文件式 skill 的统一查询入口，并对加载结果做简单缓存。
 */
@Service
public class SkillCatalogService {

    private final SkillFileLoader skillFileLoader;
    private volatile List<SkillDefinition> cachedSkills;

    public SkillCatalogService(SkillFileLoader skillFileLoader) {
        this.skillFileLoader = skillFileLoader;
    }

    /**
     * 返回全部 skill。
     */
    public List<SkillDefinition> listAllSkills() {
        return ensureLoaded();
    }

    /**
     * 返回启用中的 skill。
     */
    public List<SkillDefinition> listEnabledSkills() {
        return ensureLoaded().stream()
                .filter(SkillDefinition::enabled)
                .toList();
    }

    /**
     * 按 id 查找一个启用中的 skill。
     */
    public Optional<SkillDefinition> findEnabledSkill(String skillId) {
        return listEnabledSkills().stream()
                .filter(skill -> skill.id().equals(skillId))
                .findFirst();
    }

    /**
     * 重新加载 skill 文件。
     */
    public synchronized List<SkillDefinition> reload() {
        this.cachedSkills = List.copyOf(skillFileLoader.loadAll());
        return this.cachedSkills;
    }

    private List<SkillDefinition> ensureLoaded() {
        List<SkillDefinition> snapshot = cachedSkills;
        if (snapshot != null) {
            return snapshot;
        }
        return reload();
    }
}
