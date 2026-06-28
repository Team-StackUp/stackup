package com.stackup.stackup.coverletter.presentation.dto;

import com.stackup.stackup.coverletter.application.dto.CoverLetterCreateCommand;
import com.stackup.stackup.coverletter.application.dto.CoverLetterItem;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CoverLetterCreateRequest(
    String title,
    @NotEmpty List<Item> items
) {
    public record Item(String question, String answer) {
    }

    public CoverLetterCreateCommand toCommand() {
        List<CoverLetterItem> mapped = items == null ? List.of()
            : items.stream().map(i -> new CoverLetterItem(i.question(), i.answer())).toList();
        return new CoverLetterCreateCommand(title, mapped);
    }
}
