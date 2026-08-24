package com.stackup.stackup.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * PiiMasker 는 호출부에서만 쓰는 유틸이다. 여기서는 (1) 개별 마스킹이 의도대로 도는지와
 * (2) **전역 필터로 꽂으면 안 되는 이유**를 함께 고정한다.
 *
 * <p>(2)를 테스트로 남기는 이유: 문서에 적어두는 것만으로는 다음 사람이 Logback 컨버터로
 * 꽂는 걸 막지 못한다. 그렇게 하면 트레이스 ID 가 뭉개져 Core·AI·RealTime 로그 상관관계가
 * 통째로 깨지는데, 그건 로그를 봐야 발견된다.
 */
class PiiMaskerTest {

    @Test
    void masksEmailKeepingDomain() {
        assertThat(PiiMasker.maskEmail("hongildong@example.com")).isEqualTo("ho***@example.com");
    }

    @Test
    void masksPhoneKeepingLastFour() {
        assertThat(PiiMasker.maskPhoneNumber("010-1234-5678")).isEqualTo("***-***-5678");
    }

    @Test
    void masksGithubTokenKeepingPrefixAndTail() {
        assertThat(PiiMasker.maskGithubToken("ghp_abcdefghijklmnopqrstuvwxyz0123"))
            .startsWith("ghp")
            .endsWith("0123")
            .contains("***");
    }

    @Test
    void maskFindsPatternsInsideFreeText() {
        String masked = PiiMasker.mask("문의: hongildong@example.com 로 연락 주세요");
        assertThat(masked).doesNotContain("hongildong@").contains("example.com");
    }

    // 아래 둘이 이 클래스를 전역 필터로 쓰면 안 되는 이유다 (클래스 Javadoc 참고).
    // 실패한다면 전화번호 패턴이 개선된 것이므로, 그때 문서·Javadoc 의 근거도 함께 갱신한다.
    @Test
    void maskCorruptsTraceIds_soItMustNotBeAppliedGlobally() {
        String traceId = "01234567-89ab-cdef-0123-456789abcdef";

        assertThat(PiiMasker.mask("traceId=" + traceId)).doesNotContain(traceId);
    }

    @Test
    void maskCorruptsEpochMillis_soItMustNotBeAppliedGlobally() {
        assertThat(PiiMasker.mask("endedAt=1755993600000")).doesNotContain("1755993600000");
    }
}
