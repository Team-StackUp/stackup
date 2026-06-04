package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.InterviewMessageService;
import com.stackup.stackup.session.presentation.dto.MessageResponse;
import com.stackup.stackup.session.presentation.dto.MessageSubmitRequest;
import com.stackup.stackup.session.application.InterviewMessageService.AudioStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Session Messages", description = "면접 메시지 시퀀스 — 질문/답변 트리.")
@RestController
@RequestMapping("/api/sessions/{sessionId}/messages")
@RequiredArgsConstructor
public class InterviewMessageController {

    private final InterviewMessageService messageService;

    @Operation(operationId = "listSessionMessages", summary = "세션 메시지 시퀀스 (US-20)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "메시지 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @GetMapping
    public List<MessageResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return messageService.list(principal.userId(), sessionId).stream()
            .map(MessageResponse::from)
            .toList();
    }

    @Operation(
        operationId = "submitAnswer",
        summary = "답변 제출 (US-19) + 꼬리질문 자동 트리거",
        description = "직전 INTERVIEWER 메시지에 대한 답변을 INTERVIEWEE 로 INSERT. commit 후 generate.followup 발행."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "답변 저장 + 꼬리질문 발행"),
        @ApiResponse(responseCode = "400", description = "검증 실패"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "세션이 IN_PROGRESS 아니거나 직전 메시지가 질문 아님")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse submit(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody MessageSubmitRequest request
    ) {
        return MessageResponse.from(
            messageService.submitAnswer(principal.userId(), sessionId, request.content(), idempotencyKey)
        );
    }

    @Operation(
        operationId = "streamMessageAudio",
        summary = "메시지 오디오 스트리밍 (질문 TTS / 음성 답변)",
        description = "MinIO presigned URL 이 내부 호스트라 브라우저가 직접 접근 불가하므로 "
            + "Core 가 인증을 거쳐 오디오 바이트를 스트리밍한다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "오디오 바이트"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션/메시지/오디오 없음")
    })
    @GetMapping("/{messageId}/audio")
    public ResponseEntity<Resource> audio(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @PathVariable Long messageId
    ) {
        AudioStream audio = messageService.streamAudio(principal.userId(), sessionId, messageId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(audio.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .body(new InputStreamResource(audio.stream()));
    }

    @Operation(
        operationId = "streamMessageAudioSegment",
        summary = "라이브 TTS 문장 세그먼트 스트리밍",
        description = "AI 서버가 S3에 쓴 per-sentence TTS 세그먼트를 규칙 기반 키로 프록시한다. "
            + "세그먼트는 DB에 미저장(휘발성 라이브)이므로 Core 가 키를 재구성해 스트리밍."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "세그먼트 오디오 바이트"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션/메시지/세그먼트 없음")
    })
    @GetMapping("/{messageId}/audio/segments/{seq}")
    public ResponseEntity<Resource> audioSegment(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @PathVariable Long messageId,
        @PathVariable int seq,
        @RequestParam String ext
    ) {
        var audio = messageService.streamAudioSegment(principal.userId(), sessionId, messageId, seq, ext);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(audio.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
            .body(new InputStreamResource(audio.stream()));
    }
}
