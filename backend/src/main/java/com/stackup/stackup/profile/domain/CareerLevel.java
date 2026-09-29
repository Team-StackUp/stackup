package com.stackup.stackup.profile.domain;

/** 경력 수준. 질문 난이도와 묻는 대상(가정 vs 실제 경험)을 가른다. */
public enum CareerLevel {
    NEW("신입"),
    EXPERIENCED("경력"),
    INTERN("인턴·체험형"),
    CAREER_CHANGE("직무 전환");

    private final String koreanLabel;

    CareerLevel(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    public String koreanLabel() {
        return koreanLabel;
    }
}
