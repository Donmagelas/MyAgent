package com.example.agentplatform.advisor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求安全分类 prompt 完整性测试。
 * 锁定泛用风险覆盖面，避免后续退化为只处理某几个例子。
 */
class SpringAiRequestSafetyClassifierPromptTest {

    @Test
    void shouldContainBroadSafetyCoverage() {
        String prompt = SpringAiRequestSafetyClassifier.systemPromptTemplate();

        assertThat(prompt).contains("credential");
        assertThat(prompt).contains("unauthorized access");
        assertThat(prompt).contains("violent wrongdoing");
        assertThat(prompt).contains("explosives");
        assertThat(prompt).contains("fraud");
        assertThat(prompt).contains("self-harm");
        assertThat(prompt).contains("privacy invasion");
        assertThat(prompt).contains("defense, prevention, auditing");
        assertThat(prompt).contains("Do not block a request merely because it mentions words like password");
        assertThat(prompt).contains("MALWARE_OR_CYBER_ABUSE");
        assertThat(prompt).contains("VIOLENCE_OR_WEAPONIZATION");
        assertThat(prompt).contains("ILLEGAL_OR_DANGEROUS_ACTIVITY");
    }
}
