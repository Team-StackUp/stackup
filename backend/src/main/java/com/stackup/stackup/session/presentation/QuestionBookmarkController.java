package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.QuestionBookmarkService;
import com.stackup.stackup.session.presentation.dto.BookmarkedQuestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// UserStatsController 와 같은 이유로 session 슬라이스에 둔다 — URL 은 /api/users/me/* 지만
// 데이터 출처가 session 도메인이다(user → session 직접 의존 회피).
@Tag(name = "Users (Bookmarks)", description = "오답노트 — 다시 볼 질문 모아보기.")
@RestController
@RequiredArgsConstructor
public class QuestionBookmarkController {

    private final QuestionBookmarkService bookmarkService;

    @Operation(
        operationId = "listBookmarkedQuestions",
        summary = "오답노트 목록",
        description = "표시해 둔 질문과 그때 내 답변·모범 답안·코칭을 함께 반환한다. "
            + "표시/해제는 PUT /api/sessions/{sessionId}/messages/{messageId}/bookmark."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "최근 표시 순 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/api/users/me/bookmarks")
    public List<BookmarkedQuestionResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return bookmarkService.list(principal.userId()).stream()
            .map(BookmarkedQuestionResponse::from)
            .toList();
    }
}
