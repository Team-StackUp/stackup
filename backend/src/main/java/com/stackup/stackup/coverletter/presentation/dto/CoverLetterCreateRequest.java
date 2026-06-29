package com.stackup.stackup.coverletter.presentation.dto;

import com.stackup.stackup.coverletter.application.dto.CoverLetterCreateCommand;
import com.stackup.stackup.coverletter.application.dto.CoverLetterItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// 서버측 입력 상한 — JSON @RequestBody 는 multipart 20MB 제한이 적용되지 않으므로
// 거대 payload(DoS·AI 비용 증폭)를 bean-validation 으로 차단한다. 상한은 프론트 입력 캡과 일치.
public record CoverLetterCreateRequest(
    @Size(max = 200) String title,
    @NotEmpty @Size(max = 30) List<@Valid Item> items
) {
    public record Item(
        @Size(max = 500) String question,
        @Size(max = 5000) String answer
    ) {
    }

    public CoverLetterCreateCommand toCommand() {
        List<CoverLetterItem> mapped = items == null ? List.of()
            : items.stream().map(i -> new CoverLetterItem(i.question(), i.answer())).toList();
        return new CoverLetterCreateCommand(title, mapped);
    }
}
