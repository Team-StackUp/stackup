package com.stackup.stackup.session.presentation.dto;

// 오답노트 표시 상태. 토글이 아니라 명시적 상태를 받는다 —
// 토글이면 중복 요청(더블클릭·재전송)이 상태를 뒤집어 놓는다.
public record QuestionBookmarkRequest(
    Boolean bookmarked
) {
    public boolean bookmarkedOrDefault() {
        return Boolean.TRUE.equals(bookmarked);
    }
}
