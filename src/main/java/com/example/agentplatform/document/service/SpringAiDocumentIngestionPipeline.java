package com.example.agentplatform.document.service;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.document.domain.DocumentImportSource;
import com.example.agentplatform.document.domain.KnowledgeDocument;
import com.example.agentplatform.document.domain.ParsedDocument;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import com.example.agentplatform.document.dto.DocumentImportResponse;
import com.example.agentplatform.document.repository.KnowledgeDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Spring AI ETL 的文档导入管线。
 * 统一执行 reader -> metadata enrich -> section split -> chunk split -> chunk enhance -> writer。
 */
@Component
public class SpringAiDocumentIngestionPipeline {

    private final SpringAiDocumentReaderFactory springAiDocumentReaderFactory;
    private final MetadataEnrichingDocumentTransformer metadataEnrichingDocumentTransformer;
    private final SectionAwareDocumentTransformer sectionAwareDocumentTransformer;
    private final SectionAwareChunkingService sectionAwareChunkingService;
    private final ChunkMetadataDocumentTransformer chunkMetadataDocumentTransformer;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkDocumentWriter knowledgeChunkDocumentWriter;
    private final CompositeDocumentWriter compositeDocumentWriter;

    public SpringAiDocumentIngestionPipeline(
            SpringAiDocumentReaderFactory springAiDocumentReaderFactory,
            MetadataEnrichingDocumentTransformer metadataEnrichingDocumentTransformer,
            SectionAwareDocumentTransformer sectionAwareDocumentTransformer,
            SectionAwareChunkingService sectionAwareChunkingService,
            ChunkMetadataDocumentTransformer chunkMetadataDocumentTransformer,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeChunkDocumentWriter knowledgeChunkDocumentWriter,
            CompositeDocumentWriter compositeDocumentWriter
    ) {
        this.springAiDocumentReaderFactory = springAiDocumentReaderFactory;
        this.metadataEnrichingDocumentTransformer = metadataEnrichingDocumentTransformer;
        this.sectionAwareDocumentTransformer = sectionAwareDocumentTransformer;
        this.sectionAwareChunkingService = sectionAwareChunkingService;
        this.chunkMetadataDocumentTransformer = chunkMetadataDocumentTransformer;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkDocumentWriter = knowledgeChunkDocumentWriter;
        this.compositeDocumentWriter = compositeDocumentWriter;
    }

    /** 执行纯文本导入 ETL。 */
    public DocumentImportResponse ingestText(DocumentImportSource source, String content) {
        DocumentReader reader = springAiDocumentReaderFactory.createForText(content);
        return ingest(source, reader);
    }

    /** 执行文件导入 ETL。 */
    public DocumentImportResponse ingestFile(DocumentImportSource source, DocumentFileImportRequest request) {
        DocumentReader reader = springAiDocumentReaderFactory.createForFile(source, request);
        return ingest(source, reader);
    }

    private DocumentImportResponse ingest(DocumentImportSource source, DocumentReader reader) {
        List<Document> readDocuments = reader.read();
        KnowledgeDocument knowledgeDocument = registerKnowledgeDocument(source, readDocuments);
        DocumentTransformer metadataTransformer = metadataEnrichingDocumentTransformer.create(source);
        List<Document> enrichedDocuments = metadataTransformer.apply(readDocuments);
        DocumentTransformer sectionTransformer = sectionAwareDocumentTransformer.create(source);
        List<Document> sectionDocuments = sectionTransformer.apply(enrichedDocuments);
        List<Document> chunkDocuments = sectionAwareChunkingService.split(sectionDocuments, source);
        DocumentTransformer chunkTransformer = chunkMetadataDocumentTransformer.create(knowledgeDocument, source);
        List<Document> indexedChunkDocuments = new ArrayList<>(chunkTransformer.apply(chunkDocuments));
        DocumentWriter documentWriter = compositeDocumentWriter.bind(List.of(
                knowledgeChunkDocumentWriter
        ));
        documentWriter.write(indexedChunkDocuments);
        if (indexedChunkDocuments.isEmpty()) {
            throw new ApplicationException("Document ETL pipeline did not produce a write result");
        }
        return new DocumentImportResponse(
                knowledgeDocument.id(),
                knowledgeDocument.documentCode(),
                indexedChunkDocuments.size()
        );
    }

    private KnowledgeDocument registerKnowledgeDocument(DocumentImportSource source, List<Document> readDocuments) {
        String content = readDocuments.isEmpty() ? "" : readDocuments.get(0).getText();
        ParsedDocument parsedDocument = new ParsedDocument(
                source.title(),
                content,
                source.sourceType(),
                source.sourceUri(),
                source.metadata()
        );
        return knowledgeDocumentRepository.save(parsedDocument);
    }
}
