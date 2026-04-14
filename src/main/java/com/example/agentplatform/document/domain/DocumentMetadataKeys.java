package com.example.agentplatform.document.domain;

/**
 * 文档 metadata 统一键名常量。
 * 用于 ETL、向量检索和来源映射阶段共享同一套字段定义。
 */
public final class DocumentMetadataKeys {

    public static final String DOCUMENT_ID = "documentId";
    public static final String DOCUMENT_CODE = "documentCode";
    public static final String DOCUMENT_TITLE = "documentTitle";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String SOURCE_URI = "sourceUri";
    public static final String SECTION_INDEX = "sectionIndex";
    public static final String SECTION_TYPE = "sectionType";
    public static final String SECTION_TITLE = "sectionTitle";
    public static final String SECTION_PATH = "sectionPath";
    public static final String HEADING_TITLE = "headingTitle";
    public static final String HEADING_LEVEL = "headingLevel";
    public static final String JSON_PATH = "jsonPath";
    public static final String CHUNK_TITLE = "chunkTitle";
    public static final String CHUNK_SUMMARY = "chunkSummary";
    public static final String OVERLAP_CHARS = "overlapChars";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String CHUNK_ID = "chunkId";
    public static final String RETRIEVAL_TYPE = "retrievalType";
    public static final String VECTOR_SCORE = "vectorScore";
    public static final String KEYWORD_SCORE = "keywordScore";
    public static final String FUSION_SCORE = "fusionScore";
    public static final String RERANK_SCORE = "rerankScore";

    private DocumentMetadataKeys() {
    }
}
