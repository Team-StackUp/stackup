package com.stackup.stackup.coverletter.application.dto;

import java.util.List;

public record CoverLetterCreateCommand(
    String title,
    List<CoverLetterItem> items
) {
}
