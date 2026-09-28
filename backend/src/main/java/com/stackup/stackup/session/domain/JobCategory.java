package com.stackup.stackup.session.domain;

/**
 * 면접 직군.
 *
 * <p>처음엔 개발 4종(FRONTEND·BACKEND·INFRA·DBA)뿐이었다. 서비스가 "IT 직군"이 아니라
 * <b>취준생 전반</b>을 대상으로 하는데 직군 선택지에 개발 직무만 있어서, 개발이 아닌
 * 지원자는 시작 화면에서 고를 수 있는 게 없었다.
 *
 * <p>개발 직군은 세부 직무 단위로, 비개발 직군은 대분류 단위로 둔다. 입도가 섞이지만
 * 기존 4개를 그대로 둬야 이미 쌓인 세션·통계가 유지된다(enum 이름은 DB 에 문자열로 저장된다).
 * 더 좁은 직무는 <b>직무 맞춤 모드</b>(회사명 + 채용공고)가 받는다 — 거기가 자유 입력 창구다.
 */
public enum JobCategory {

    // ── 개발 ──────────────────────────────────────────────────────────────
    FRONTEND("프론트엔드"),
    BACKEND("백엔드"),
    INFRA("인프라·DevOps"),
    DBA("DBA"),
    MOBILE("모바일"),
    DATA_AI("데이터·AI"),
    SECURITY("보안"),
    QA("QA·테스트"),

    // ── 비개발 ────────────────────────────────────────────────────────────
    PLANNING("기획·PM"),
    MARKETING("마케팅·광고"),
    SALES("영업·영업관리"),
    HR("인사·노무"),
    FINANCE("재무·회계"),
    DESIGN("디자인"),
    MANUFACTURING("생산·품질"),
    RND("연구개발"),
    CUSTOMER_SERVICE("고객지원·CS"),
    LOGISTICS("물류·유통·구매"),
    LEGAL("법무"),
    PUBLIC("공공·행정");

    private final String koreanLabel;

    JobCategory(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    public String koreanLabel() {
        return koreanLabel;
    }

    /** 개발 직군인가. GitHub 레포 분석처럼 개발자에게만 의미 있는 자료를 안내할 때 쓴다. */
    public boolean isEngineering() {
        return switch (this) {
            case FRONTEND, BACKEND, INFRA, DBA, MOBILE, DATA_AI, SECURITY, QA -> true;
            default -> false;
        };
    }
}
