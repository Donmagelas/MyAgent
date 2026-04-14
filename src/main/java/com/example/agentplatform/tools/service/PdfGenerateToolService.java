package com.example.agentplatform.tools.service;

import com.example.agentplatform.auth.domain.SecurityRole;
import com.example.agentplatform.common.exception.ApplicationException;
import com.example.agentplatform.config.ToolProperties;
import com.example.agentplatform.tools.dto.PdfGenerateResult;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * PDF 生成工具。
 */
@Component
public class PdfGenerateToolService {

    private static final String TOOL_NAME = "generate_pdf";

    private final ToolProperties toolProperties;
    private final ToolPermissionGuard toolPermissionGuard;

    public PdfGenerateToolService(ToolProperties toolProperties, ToolPermissionGuard toolPermissionGuard) {
        this.toolProperties = toolProperties;
        this.toolPermissionGuard = toolPermissionGuard;
    }

    /**
     * 根据标题和正文生成 PDF 文件，并直接返回结果给用户。
     */
    @Tool(
            name = TOOL_NAME,
            description = "根据标题和正文内容生成 PDF 文档，并直接返回生成结果",
            returnDirect = true
    )
    public PdfGenerateResult generatePdf(
            @ToolParam(description = "PDF 标题") String title,
            @ToolParam(description = "PDF 正文内容") String content,
            ToolContext toolContext
    ) {
        toolPermissionGuard.assertAllowed(
                TOOL_NAME,
                Set.of(SecurityRole.CHAT_USER, SecurityRole.KNOWLEDGE_USER, SecurityRole.KNOWLEDGE_ADMIN),
                false,
                toolContext
        );

        try {
            Path outputDirectory = toolProperties.pdf().outputDirectory();
            Files.createDirectories(outputDirectory);
            String fileName = buildFileName(title);
            Path outputFile = outputDirectory.resolve(fileName);

            try (PdfWriter writer = new PdfWriter(outputFile.toString());
                 PdfDocument pdfDocument = new PdfDocument(writer);
                 Document document = new Document(pdfDocument)) {
                pdfDocument.getDocumentInfo().setAuthor(toolProperties.pdf().defaultAuthor());
                document.add(new Paragraph(title == null || title.isBlank() ? "Untitled PDF" : title));
                document.add(new Paragraph(content == null ? "" : content));
            }

            return new PdfGenerateResult(
                    fileName,
                    outputFile.toAbsolutePath().toString(),
                    Files.size(outputFile)
            );
        }
        catch (IOException exception) {
            throw new ApplicationException("Failed to generate PDF", exception);
        }
    }

    private String buildFileName(String title) {
        String safeTitle = title == null || title.isBlank()
                ? "generated-pdf"
                : title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5-_]+", "-");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return safeTitle + "-" + timestamp + ".pdf";
    }
}
