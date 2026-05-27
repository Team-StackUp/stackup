package com.stackup.stackup.resume.application.dto;

import java.io.InputStream;

public record ResumeUploadCommand(
    String originalFilename,
    String contentType,
    long size,
    InputStream content
) {
}
