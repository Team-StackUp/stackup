package com.stackup.stackup.resume.presentation;

import com.stackup.stackup.common.response.PageResponse;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.resume.application.ResumeService;
import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.presentation.dto.ResumeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@Tag(name = "Resumes", description = "이력서 업로드 및 관리")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    @Operation(summary = "이력서 PDF 업로드")
    public ResponseEntity<ResumeResponse> upload(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestPart("file") MultipartFile file
    ) {
        ResumeResult result = resumeService.upload(principal.userId(), new ResumeUploadCommand(file));
        return ResponseEntity
            .created(URI.create("/api/resumes/" + result.id()))
            .body(ResumeResponse.from(result));
    }

    @GetMapping
    @Operation(summary = "내 이력서 목록")
    public PageResponse<ResumeResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(resumeService.list(principal.userId(), pageable).map(ResumeResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "이력서 단건 조회")
    public ResumeResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        return ResumeResponse.from(resumeService.get(principal.userId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "이력서 소프트 삭제")
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        resumeService.delete(principal.userId(), id);
    }
}
