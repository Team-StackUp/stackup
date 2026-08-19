package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;

public record SessionResult(
    Long id,
    String title,
    String memo,
    SessionMode mode,
    JobCategory jobCategory,
    List<JobCategory> jobCategories,
    Integer maxQuestions,
    Integer maxDurationMinutes,
    Integer generalQuestionCount,
    Integer maxFollowupsPerQuestion,
    SessionStatus status,
    Integer totalQuestionCount,
    Instant startedAt,
    Instant endedAt,
    List<Long> contextDocumentIds,
    // 직무 맞춤 모드 전용. 다른 모드는 null.
    String targetCompanyName,
    String targetJobDescription,
    // 약점 집중 재도전의 겨냥 축(SessionFocusArea name). 일반 면접은 빈 목록.
    List<String> focusAreas,
    Instant createdAt,
    Instant updatedAt
) {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> FOCUS_AREA_TYPE = new TypeReference<>() {};

    // 저장은 JSON 문자열, 응답은 목록. 파싱 실패는 '집중 영역 없음'으로 흘린다(조회가 깨지면 안 된다).
    private static List<String> parseFocusAreas(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, FOCUS_AREA_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public static SessionResult of(InterviewSession session, List<Long> documentIds) {
        return new SessionResult(
            session.getId(),
            session.getTitle(),
            session.getMemo(),
            session.getMode(),
            session.getJobCategory(),
            List.copyOf(session.getJobCategories()),
            session.getMaxQuestions(),
            session.getMaxDurationMinutes(),
            session.getGeneralQuestionCount(),
            session.getMaxFollowupsPerQuestion(),
            session.getStatus(),
            session.getTotalQuestionCount(),
            session.getStartedAt(),
            session.getEndedAt(),
            documentIds,
            session.getTargetCompanyName(),
            session.getTargetJobDescription(),
            parseFocusAreas(session.getFocusAreas()),
            session.getCreatedAt(),
            session.getUpdatedAt()
        );
    }
}
