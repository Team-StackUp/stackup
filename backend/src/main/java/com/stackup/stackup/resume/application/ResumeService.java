package com.stackup.stackup.resume.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.application.event.ResumeUploadedEvent;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final AnalyzedDocumentRepository documentRepository;
    private final ObjectStorageClient storage;
    private final ApplicationEventPublisher events;

    public ResumeService(
        ResumeRepository resumeRepository,
        UserRepository userRepository,
        AnalyzedDocumentRepository documentRepository,
        ObjectStorageClient storage,
        ApplicationEventPublisher events
    ) {
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.storage = storage;
        this.events = events;
    }

    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    @Transactional
    public ResumeResult upload(Long userId, ResumeUploadCommand command) {
        MultipartFile file = command.file();
        if (file == null || file.isEmpty()) {
            throw new DomainException(ApiErrorCode.RESUME_EMPTY_FILE);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new DomainException(ApiErrorCode.RESUME_FILE_TOO_LARGE);
        }
        if (!isPdfContentType(file.getContentType()) || !hasPdfExtension(file.getOriginalFilename())) {
            throw new DomainException(ApiErrorCode.RESUME_INVALID_FILE_TYPE);
        }
        if (!hasPdfMagicBytes(file)) {
            throw new DomainException(ApiErrorCode.RESUME_INVALID_FILE_TYPE);
        }

        String key = "resumes/raw/" + userId + "/" + UUID.randomUUID() + ".pdf";
        long size = file.getSize();

        try (var in = file.getInputStream()) {
            storage.put(key, in, size, "application/pdf");
        } catch (java.io.IOException e) {
            throw new DomainException(ApiErrorCode.SYS_INTERNAL_ERROR, "Failed to read uploaded file", e);
        }

        Resume resume;
        try {
            User user = userRepository.getReferenceById(userId);
            resume = resumeRepository.save(Resume.create(
                user, file.getOriginalFilename(), key, size, ResumeFileType.PDF
            ));
        } catch (RuntimeException dbError) {
            safeDelete(key);
            throw dbError;
        }

        String traceId = MDC.get("traceId");
        events.publishEvent(new ResumeUploadedEvent(resume.getId(), userId, key, traceId));
        return ResumeResult.from(resume);
    }

    private void safeDelete(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isPdfContentType(String contentType) {
        return contentType != null && contentType.equalsIgnoreCase("application/pdf");
    }

    private static boolean hasPdfExtension(String filename) {
        return filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }

    private static boolean hasPdfMagicBytes(MultipartFile file) {
        try (var in = file.getInputStream()) {
            byte[] head = in.readNBytes(PDF_MAGIC.length);
            return java.util.Arrays.equals(head, PDF_MAGIC);
        } catch (java.io.IOException e) {
            throw new DomainException(ApiErrorCode.RESUME_INVALID_FILE_TYPE, "Failed to read uploaded file", e);
        }
    }
}
