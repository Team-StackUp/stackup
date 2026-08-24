package com.stackup.stackup.coverletter.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.coverletter.application.dto.CoverLetterCreateCommand;
import com.stackup.stackup.coverletter.application.dto.CoverLetterItem;
import com.stackup.stackup.coverletter.application.dto.CoverLetterResult;
import com.stackup.stackup.coverletter.application.event.CoverLetterDeletedEvent;
import com.stackup.stackup.coverletter.application.event.CoverLetterUploadedEvent;
import com.stackup.stackup.coverletter.domain.CoverLetter;
import com.stackup.stackup.coverletter.domain.CoverLetterRepository;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverLetterService {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<CoverLetterItem>> ITEM_LIST = new TypeReference<>() {};

    private final CoverLetterRepository coverLetterRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public CoverLetterResult create(Long userId, CoverLetterCreateCommand command) {
        List<CoverLetterItem> items = sanitize(command.items());
        if (items.isEmpty()) {
            throw new DomainException(ApiErrorCode.COVER_LETTER_EMPTY);
        }
        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));

        CoverLetter coverLetter = coverLetterRepository.save(
            CoverLetter.create(user, command.title(), serialize(items))
        );
        // document 도메인 listener 가 동일 트랜잭션 안에서 AnalyzedDocument(PROCESSING) 생성 + AFTER_COMMIT 으로 analyze.cover_letter 발행.
        events.publishEvent(new CoverLetterUploadedEvent(userId, coverLetter.getId()));
        return CoverLetterResult.of(coverLetter, items);
    }

    public List<CoverLetterResult> list(Long userId) {
        return coverLetterRepository.findByUser_IdAndDeletedFalseOrderByIdDesc(userId).stream()
            .map(cl -> CoverLetterResult.of(cl, parse(cl.getItems())))
            .toList();
    }

    @Transactional
    public void delete(Long userId, Long coverLetterId) {
        CoverLetter coverLetter = loadOwned(userId, coverLetterId);
        coverLetter.markDeleted();
        // 문항 원문 즉시 파기 — 자소서 본문은 S3 가 아니라 이 행 안에 살아서, cascade 파기
        // (분석 마크다운·임베딩)만으로는 "지웠는데 남아 있는" 상태가 된다.
        coverLetter.purgeItems();
        // 분석 결과 cascade — document 도메인 listener 가 받아 AnalyzedDocument soft delete
        // + 분석 내용물 파기 (도메인 cycle 회피).
        events.publishEvent(new CoverLetterDeletedEvent(userId, coverLetterId));
    }

    private CoverLetter loadOwned(Long userId, Long coverLetterId) {
        return coverLetterRepository.findByIdAndUser_IdAndDeletedFalse(coverLetterId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.COVER_LETTER_NOT_FOUND));
    }

    private List<CoverLetterItem> sanitize(List<CoverLetterItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .map(it -> new CoverLetterItem(
                it.question() == null ? "" : it.question().trim(),
                it.answer() == null ? "" : it.answer().trim()))
            .filter(it -> !it.answer().isBlank())  // 답변 없는 문항은 버린다.
            .toList();
    }

    private String serialize(List<CoverLetterItem> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new DomainException(ApiErrorCode.SYS_INTERNAL_ERROR);
        }
    }

    private List<CoverLetterItem> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, ITEM_LIST);
        } catch (JsonProcessingException e) {
            // 자소서 답변 본문(PII)은 로그에 남기지 않는다 — 길이만 기록.
            log.warn("cover letter items parse failed, return empty. length={}", json.length(), e);
            return List.of();
        }
    }
}
