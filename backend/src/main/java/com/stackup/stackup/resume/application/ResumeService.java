package com.stackup.stackup.resume.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.application.event.ResumeDeletedEvent;
import com.stackup.stackup.resume.application.event.ResumeUploadedEvent;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB (compose multipart 한도와 동일)
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String KEY_PREFIX = "resumes/raw";

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ObjectStorageClient storage;
    private final ApplicationEventPublisher events;

    @Transactional
    public ResumeResult upload(Long userId, ResumeUploadCommand command) {
        validate(command);

        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        String key = buildKey(userId);
        storage.put(key, command.content(), command.size(), PDF_CONTENT_TYPE);

        Resume resume = resumeRepository.save(
            Resume.create(user, command.originalFilename(), key, ResumeFileType.PDF, command.size())
        );

        // document 도메인 listener 가 동일 트랜잭션 안에서 AnalyzedDocument(PROCESSING) 생성 + AFTER_COMMIT 으로 analyze.resume 발행.
        events.publishEvent(new ResumeUploadedEvent(userId, resume.getId()));
        return ResumeResult.of(resume);
    }

    public List<ResumeResult> list(Long userId) {
        return resumeRepository.findByUser_IdAndDeletedFalse(userId).stream()
            .map(ResumeResult::of)
            .toList();
    }

    public ResumeResult get(Long userId, Long resumeId) {
        return ResumeResult.of(loadOwned(userId, resumeId));
    }

    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = loadOwned(userId, resumeId);
        resume.markDeleted();
        // 분석 결과 cascade — document 도메인 listener 가 ResumeDeletedEvent 받아 AnalyzedDocument soft delete.
        // 직접 의존 회피 (ArchUnit 도메인 cycle 방지).
        events.publishEvent(new ResumeDeletedEvent(userId, resumeId));
    }

    private Resume loadOwned(Long userId, Long resumeId) {
        return resumeRepository.findByIdAndUser_IdAndDeletedFalse(resumeId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.RESUME_NOT_FOUND));
    }

    private void validate(ResumeUploadCommand command) {
        if (command.size() <= 0) {
            throw new DomainException(ApiErrorCode.RESUME_EMPTY_FILE);
        }
        if (command.size() > MAX_FILE_SIZE_BYTES) {
            throw new DomainException(ApiErrorCode.RESUME_FILE_TOO_LARGE);
        }
        String name = command.originalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new DomainException(ApiErrorCode.RESUME_INVALID_FILE_TYPE);
        }
        String contentType = command.contentType();
        if (contentType != null && !contentType.equalsIgnoreCase(PDF_CONTENT_TYPE)) {
            throw new DomainException(ApiErrorCode.RESUME_INVALID_FILE_TYPE);
        }
    }

    private String buildKey(Long userId) {
        // resumes/raw/{userId}/{uuid}.pdf — docs/storage.md §2 컨벤션
        return KEY_PREFIX + "/" + userId + "/" + UUID.randomUUID() + ".pdf";
    }
}
