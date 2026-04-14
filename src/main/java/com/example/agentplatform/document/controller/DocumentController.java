package com.example.agentplatform.document.controller;

import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.document.dto.DocumentFileImportRequest;
import com.example.agentplatform.document.dto.DocumentImportRequest;
import com.example.agentplatform.document.dto.DocumentImportResponse;
import com.example.agentplatform.document.service.DocumentIngestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 文档导入控制器。
 * 对外提供 Phase 1 的文本知识导入接口。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentIngestionService documentIngestionService, ObjectMapper objectMapper) {
        this.documentIngestionService = documentIngestionService;
        this.objectMapper = objectMapper;
    }

    /** 把纯文本导入知识库。 */
    @PostMapping("/import-text")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentImportResponse> importText(@Valid @RequestBody DocumentImportRequest request) {
        return Mono.fromCallable(() -> documentIngestionService.importText(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 把 txt、md、json 文件导入知识库。 */
    @PostMapping(value = "/import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DocumentImportResponse> importFile(
            @RequestPart("file") FilePart file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sourceUri", required = false) String sourceUri,
            @RequestParam(value = "metadata", required = false) String metadataJson
    ) {
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> Mono.fromCallable(() -> documentIngestionService.importFile(new DocumentFileImportRequest(
                                title,
                                sourceUri,
                                parseMetadata(metadataJson),
                                file.filename(),
                                file.headers().getContentType() == null ? null : file.headers().getContentType().toString(),
                                bytes
                        )))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (Exception exception) {
            throw new ApplicationException("Invalid metadata json", exception);
        }
    }
}
