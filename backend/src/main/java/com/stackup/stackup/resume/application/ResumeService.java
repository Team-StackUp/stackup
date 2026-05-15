package com.stackup.stackup.resume.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.user.domain.UserRepository;
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

    @Transactional
    public ResumeResult upload(Long userId, ResumeUploadCommand command) {
        MultipartFile file = command.file();
        if (file == null || file.isEmpty()) {
            throw new DomainException(ApiErrorCode.RESUME_EMPTY_FILE);
        }
        throw new UnsupportedOperationException("not yet");
    }
}
