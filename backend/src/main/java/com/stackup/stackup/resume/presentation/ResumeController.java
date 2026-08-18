package com.stackup.stackup.resume.presentation;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.resume.application.ResumeService;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.presentation.dto.ResumeResponse;
import com.stackup.stackup.resume.presentation.dto.WebResumeCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Resumes", description = "이력서 업로드/관리. 업로드 시 분석 파이프라인이 자동 트리거되며 결과는 /realtime/stream/me (DOC_STATE) 로 통지됨.")
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @Operation(
        operationId = "uploadResume",
        summary = "이력서 PDF 업로드 + 분석 트리거",
        description = "multipart/form-data 로 PDF 파일을 받아 S3 저장 → DB row 생성(status=PENDING) → AI 분석 자동 발행. 최대 20MB, application/pdf 만 허용."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "업로드 + 분석 트리거 성공"),
        @ApiResponse(responseCode = "400", description = "빈 파일 / 사이즈 초과 / 비-PDF"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse upload(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ApiErrorCode.RESUME_EMPTY_FILE);
        }
        try {
            ResumeUploadCommand command = new ResumeUploadCommand(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream()
            );
            return ResumeResponse.from(resumeService.upload(principal.userId(), command));
        } catch (IOException e) {
            throw new DomainException(ApiErrorCode.SYS_INTERNAL_ERROR);
        }
    }

    @Operation(
        operationId = "registerWebResume",
        summary = "웹 이력서(URL) 등록 + 분석 트리거 (US-09)",
        description = "포트폴리오·블로그·노션 등 공개 URL 을 이력서 자료로 등록한다. 파일 업로드 없이 "
            + "URL 만 저장하고, 본문 추출·요약·임베딩은 AI 서버가 수행(analyze.web). 결과는 "
            + "/realtime/stream/me (DOC_STATE) 로 통지된다. http·https 공개 주소만 허용(내부망 차단)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록 + 분석 트리거 성공"),
        @ApiResponse(responseCode = "400", description = "URL 형식 오류 / 비-http(s) / 내부망 주소"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "409", description = "이미 등록된 URL")
    })
    @PostMapping(value = "/web", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse registerWeb(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody WebResumeCreateRequest request
    ) {
        return ResumeResponse.from(resumeService.registerWeb(principal.userId(), request.url()));
    }

    @Operation(operationId = "listResumes", summary = "내 이력서 목록")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자 소유 이력서 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public List<ResumeResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return resumeService.list(principal.userId()).stream()
            .map(ResumeResponse::from)
            .toList();
    }

    @Operation(operationId = "getResume", summary = "이력서 단건 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이력서 메타"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "이력서 없음")
    })
    @GetMapping("/{resumeId}")
    public ResumeResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long resumeId
    ) {
        return ResumeResponse.from(resumeService.get(principal.userId(), resumeId));
    }

    @Operation(operationId = "deleteResume", summary = "이력서 soft delete")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 완료"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "이력서 없음")
    })
    @DeleteMapping("/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long resumeId
    ) {
        resumeService.delete(principal.userId(), resumeId);
    }
}
