package com.stackup.stackup.session.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.session.application.dto.FeedbackResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedbackResponseTest {

    private FeedbackResult result() {
        return new FeedbackResult(1L, 50L, 80.0, 80.0, 80.0, 80.0,
            "s", "w", List.of(), List.of(), List.of(), List.of(),
            "feedback/50/report.md", "tok-1", Instant.EPOCH);
    }

    @Test
    void from_exposesReportFilePathAndShareTokenToOwner() {
        FeedbackResponse res = FeedbackResponse.from(result());

        assertThat(res.reportFilePath()).isEqualTo("feedback/50/report.md");
        assertThat(res.shareToken()).isEqualTo("tok-1");
    }

    @Test
    void fromPublic_hidesInternalStorageKeyAndShareToken() {
        // 공개(비인증) 응답 — 내부 S3 키·공유 토큰 재유출 면을 응답 본문에서 제거한다.
        // 리포트 프록시는 소유자 전용이라 공개 화면에선 키가 있어도 쓸 수 없다.
        FeedbackResponse res = FeedbackResponse.fromPublic(result());

        assertThat(res.reportFilePath()).isNull();
        assertThat(res.shareToken()).isNull();
        assertThat(res.overallScore()).isEqualTo(80.0);
    }
}
