package com.stackup.stackup.resume.presentation;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.resume.application.ResumeService;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.presentation.dto.ResumeResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

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

    @GetMapping
    public List<ResumeResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return resumeService.list(principal.userId()).stream()
            .map(ResumeResponse::from)
            .toList();
    }

    @GetMapping("/{resumeId}")
    public ResumeResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long resumeId
    ) {
        return ResumeResponse.from(resumeService.get(principal.userId(), resumeId));
    }

    @DeleteMapping("/{resumeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long resumeId
    ) {
        resumeService.delete(principal.userId(), resumeId);
    }
}
