package com.example.agentplatform.rag.service;

/**
 * 向量生成能力抽象。
 * 避免导入与检索流程依赖具体向量提供方细节。
 */
public interface EmbeddingService {

    /** 为指定文本生成一个 embedding 向量。 */
    float[] embed(String text, String stepName);
}
