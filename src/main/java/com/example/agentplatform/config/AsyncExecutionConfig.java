package com.example.agentplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行配置。
 * 当前先为长期记忆自动提炼提供一个小型线程池，避免阻塞主对话响应链路。
 */
@Configuration
public class AsyncExecutionConfig {

    /**
     * 提供长期记忆自动提炼使用的异步执行器。
     */
    @Bean("memoryExtractionTaskExecutor")
    public TaskExecutor memoryExtractionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("memory-extraction-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.initialize();
        return executor;
    }
}
