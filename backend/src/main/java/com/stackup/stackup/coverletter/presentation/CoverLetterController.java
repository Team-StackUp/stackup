package com.stackup.stackup.coverletter.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.coverletter.application.CoverLetterService;
import com.stackup.stackup.coverletter.presentation.dto.CoverLetterCreateRequest;
import com.stackup.stackup.coverletter.presentation.dto.CoverLetterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CoverLetters", description = "자소서(공채용 문항별 텍스트) 입력/관리. 생성 시 분석 파이프라인이 자동 트리거되며 결과는 /realtime/stream/me (DOC_STATE) 로 통지됨.")
@RestController
@RequestMapping("/api/cover-letters")
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    @Operation(
        operationId = "createCoverLetter",
        summary = "자소서 입력 + 분석 트리거",
        description = "문항별(질문+답변) 텍스트 자소서를 받아 DB row 생성(status=PENDING) → AI 분석 자동 발행. 답변이 비어있지 않은 문항이 1개 이상 필요."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성 + 분석 트리거 성공"),
        @ApiResponse(responseCode = "400", description = "문항 없음 / 빈 답변"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoverLetterResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CoverLetterCreateRequest request
    ) {
        return CoverLetterResponse.from(
            coverLetterService.create(principal.userId(), request.toCommand()));
    }

    @Operation(operationId = "listCoverLetters", summary = "내 자소서 목록")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자 소유 자소서 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public List<CoverLetterResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return coverLetterService.list(principal.userId()).stream()
            .map(CoverLetterResponse::from)
            .toList();
    }

    @Operation(operationId = "deleteCoverLetter", summary = "자소서 soft delete")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 완료"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "자소서 없음")
    })
    @DeleteMapping("/{coverLetterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long coverLetterId
    ) {
        coverLetterService.delete(principal.userId(), coverLetterId);
    }
}
