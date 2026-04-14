package com.example.agentplatform.rag.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.AiModelProperties;
import com.example.agentplatform.config.VectorStoreProperties;
import com.example.agentplatform.observability.domain.ModelUsageRecord;
import com.example.agentplatform.observability.service.ModelUsageLogService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * DashScope 多模态向量客户端。
 * 直接调用 qwen3-vl-embedding，并记录每次向量请求的 usage。
 */
@Service
public class SpringAiEmbeddingService implements EmbeddingService, EmbeddingModel {

    private static final String MULTIMODAL_EMBEDDING_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";

    private final RestClient restClient;
    private final AiModelProperties aiModelProperties;
    private final VectorStoreProperties vectorStoreProperties;
    private final ModelUsageLogService modelUsageLogService;
    private final String dashScopeApiKey;

    public SpringAiEmbeddingService(
            RestClient.Builder restClientBuilder,
            AiModelProperties aiModelProperties,
            VectorStoreProperties vectorStoreProperties,
            ModelUsageLogService modelUsageLogService,
            @Value("${spring.ai.dashscope.api-key:}") String dashScopeApiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.aiModelProperties = aiModelProperties;
        this.vectorStoreProperties = vectorStoreProperties;
        this.modelUsageLogService = modelUsageLogService;
        this.dashScopeApiKey = dashScopeApiKey;
    }

    /** 生成一个向量，并记录接口返回的 usage 信息。 */
    @Override
    public float[] embed(String text, String stepName) {
        return embed(text, stepName, null);
    }

    /**
     * 生成一个向量，并把 workflowId 写入 usage 记录，便于工作流级可视化聚合。
     */
    public float[] embed(String text, String stepName, Long workflowId) {
        return embedSingle(text, stepName, workflowId).vector();
    }

    /**
     * 适配 Spring AI EmbeddingModel，供 PgVectorStore 和 RAG 组件统一调用。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request == null ? List.of() : request.getInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return new EmbeddingResponse(List.of(), new EmbeddingResponseMetadata(aiModelProperties.embeddingModel(), null));
        }

        List<Embedding> embeddings = new java.util.ArrayList<>(instructions.size());
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        for (int index = 0; index < instructions.size(); index++) {
            EmbeddingCallResult result = embedSingle(instructions.get(index), "spring-ai-embedding", null);
            embeddings.add(new Embedding(result.vector(), index));
            if (result.usage() != null) {
                promptTokens += defaultZero(result.usage().inputTokens());
                completionTokens += defaultZero(result.usage().outputTokens());
                totalTokens += defaultZero(result.usage().totalTokens());
            }
        }
        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata(
                aiModelProperties.embeddingModel(),
                new SimpleUsage(promptTokens, completionTokens, totalTokens)
        );
        return new EmbeddingResponse(embeddings, metadata);
    }

    /**
     * 适配 Spring AI 的文档向量化入口。
     */
    @Override
    public float[] embed(Document document) {
        if (document == null) {
            throw new ApplicationException("Embedding document is null");
        }
        return embedSingle(document.getText(), "spring-ai-document-embedding", null).vector();
    }

    /**
     * 返回当前 embedding 维度，供 Spring AI 向量存储组件读取。
     */
    @Override
    public int dimensions() {
        return vectorStoreProperties.dimensions();
    }

    private String resolveApiKey() {
        String apiKey = dashScopeApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApplicationException("DashScope API key is missing");
        }
        return apiKey;
    }

    private EmbeddingCallResult embedSingle(String text, String stepName, Long workflowId) {
        Instant start = Instant.now();
        try {
            MultimodalEmbeddingRequest request = new MultimodalEmbeddingRequest(
                    aiModelProperties.embeddingModel(),
                    new MultimodalEmbeddingInput(List.of(new MultimodalContent(text))),
                    new MultimodalParameters(vectorStoreProperties.dimensions())
            );
            MultimodalEmbeddingResponse response = restClient.post()
                    .uri(MULTIMODAL_EMBEDDING_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MultimodalEmbeddingResponse.class);

            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            if (response == null || response.output() == null || response.output().embeddings() == null
                    || response.output().embeddings().isEmpty()) {
                throw new ApplicationException("Empty embedding response");
            }

            UsagePayload usage = response.usage();
            modelUsageLogService.save(new ModelUsageRecord(
                    workflowId,
                    null,
                    null,
                    null,
                    response.requestId(),
                    stepName,
                    aiModelProperties.embeddingModel(),
                    "dashscope",
                    usage == null ? null : usage.inputTokens(),
                    usage == null ? null : usage.outputTokens(),
                    usage == null ? null : usage.totalTokens(),
                    latencyMs,
                    true,
                    null
            ));
            return new EmbeddingCallResult(toFloatArray(response.output().embeddings().get(0).embedding()), usage);
        }
        catch (Exception exception) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            modelUsageLogService.save(new ModelUsageRecord(
                    workflowId,
                    null,
                    null,
                    null,
                    null,
                    stepName,
                    aiModelProperties.embeddingModel(),
                    "dashscope",
                    null,
                    null,
                    null,
                    latencyMs,
                    false,
                    exception.getMessage()
            ));
            throw new ApplicationException("Embedding generation failed", exception);
        }
    }

    private float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).floatValue();
        }
        return result;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** 提供方请求载荷。 */
    private record MultimodalEmbeddingRequest(
            String model,
            MultimodalEmbeddingInput input,
            MultimodalParameters parameters
    ) {
    }

    private record MultimodalEmbeddingInput(
            List<MultimodalContent> contents
    ) {
    }

    private record MultimodalContent(
            String text
    ) {
    }

    private record MultimodalParameters(
            Integer dimension
    ) {
    }

    /** 提供方响应载荷。 */
    private record MultimodalEmbeddingResponse(
            @JsonProperty("request_id") String requestId,
            OutputPayload output,
            UsagePayload usage
    ) {
    }

    private record OutputPayload(
            List<EmbeddingPayload> embeddings
    ) {
    }

    private record EmbeddingPayload(
            Integer index,
            List<Double> embedding,
            String type
    ) {
    }

    private record UsagePayload(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }

    /**
     * Spring AI EmbeddingModel 调用时返回的单次向量结果。
     */
    private record EmbeddingCallResult(
            float[] vector,
            UsagePayload usage
    ) {
    }

    /**
     * Spring AI EmbeddingResponse 所需的轻量 usage 适配对象。
     */
    private record SimpleUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) implements Usage {

        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        @Override
        public Integer getTotalTokens() {
            return totalTokens;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
