package com.stackup.stackup.resume.application.dto;

import org.springframework.web.multipart.MultipartFile;

public record ResumeUploadCommand(MultipartFile file) {
}
