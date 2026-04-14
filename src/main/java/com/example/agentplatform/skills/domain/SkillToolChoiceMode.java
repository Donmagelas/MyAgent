package com.example.agentplatform.skills.domain;

/**
 * Skill 对工具的选择模式。
 */
public enum SkillToolChoiceMode {

    /**
     * 仅允许使用 skill 元数据中声明的工具。
     */
    ALLOWED,

    /**
     * 不限制工具范围。
     */
    ALL,

    /**
     * 当前 skill 不允许使用任何工具。
     */
    NONE
}
