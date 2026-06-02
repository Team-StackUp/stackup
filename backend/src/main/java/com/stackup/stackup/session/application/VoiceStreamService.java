package com.stackup.stackup.session.application;

import com.stackup.stackup.session.application.VoiceAnswerUploadService.VoicePlaceholder;
import com.stackup.stackup.session.application.dto.VoiceStreamBeginResult;
import com.stackup.stackup.session.domain.InterviewMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 스트리밍 음성 답변 시작: placeholder 메시지만 생성(S3/analyze.voice 없음).
// 실제 오디오는 RealTime WS → AI WS 로 흐르고, 종료 시 AI 가 callback.voice 발행.
@Service
@RequiredArgsConstructor
public class VoiceStreamService {

    private final VoiceAnswerUploadService uploadService;

    public VoiceStreamBeginResult begin(Long userId, Long sessionId, String idempotencyKey) {
        VoicePlaceholder vp = uploadService.createVoicePlaceholder(userId, sessionId, idempotencyKey);
        InterviewMessage placeholder = vp.placeholder();
        Long parentId = vp.parentQuestion() != null ? vp.parentQuestion().getId() : null;
        return new VoiceStreamBeginResult(placeholder.getId(), parentId, placeholder.getSequenceNumber());
    }
}
