package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.VoiceAnswerUploadService;
import com.stackup.stackup.session.application.VoiceStreamService;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.VoiceAnswerUploadCommand;
import com.stackup.stackup.session.presentation.dto.MessageResponse;
import com.stackup.stackup.session.presentation.dto.VoiceStreamBeginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Voice Answer", description = "음성 답변 업로드 (Phase 2 - 음성 면접)")
@RestController
@RequestMapping("/api/sessions/{sessionId}/messages/voice")
@RequiredArgsConstructor
public class VoiceAnswerController {

    private final VoiceAnswerUploadService uploadService;
    private final VoiceStreamService streamService;

    @Operation(
        operationId = "submitVoiceAnswer",
        summary = "음성 답변 업로드 + STT/분석 트리거",
        description = "multipart/form-data 로 audio 파일 업로드. 응답은 transcribing 상태 메시지. "
            + "STT 완료 후 SESSION_MESSAGE SSE 로 transcript 가 도착합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "메시지 INSERT + analyze.voice 발행"),
        @ApiResponse(responseCode = "400", description = "파일 형식/크기 오류"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "세션이 IN_PROGRESS 아니거나 직전 메시지가 질문 아님")
    })
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse submit(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestPart("audio") MultipartFile audio
    ) throws IOException {
        VoiceAnswerUploadCommand cmd = new VoiceAnswerUploadCommand(
            audio.getInputStream(),
            audio.getSize(),
            audio.getContentType(),
            audio.getOriginalFilename(),
            idempotencyKey
        );
        MessageResult result = uploadService.submit(principal.userId(), sessionId, cmd);
        return MessageResponse.from(result);
    }

    @Operation(
        operationId = "beginVoiceStream",
        summary = "실시간 음성 답변 시작 (placeholder 생성)",
        description = "WS 오디오 스트림 전에 호출. placeholder 메시지를 만들고 messageId 를 반환. "
            + "이 messageId 를 WS 쿼리로 넘긴다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "placeholder 생성"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "세션 상태/직전 메시지 조건 불만족")
    })
    @PostMapping("/stream-begin")
    @ResponseStatus(HttpStatus.CREATED)
    public VoiceStreamBeginResponse beginStream(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return VoiceStreamBeginResponse.from(streamService.begin(principal.userId(), sessionId, idempotencyKey));
    }
}
