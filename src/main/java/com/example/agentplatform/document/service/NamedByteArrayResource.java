package com.example.agentplatform.document.service;

import org.springframework.core.io.ByteArrayResource;

/**
 * 带文件名的字节资源。
 * 供 Spring AI Reader 在处理上传文件时保留原始文件名。
 */
public class NamedByteArrayResource extends ByteArrayResource {

    private final String filename;

    public NamedByteArrayResource(byte[] byteArray, String filename) {
        super(byteArray);
        this.filename = filename;
    }

    @Override
    public String getFilename() {
        return filename;
    }
}
