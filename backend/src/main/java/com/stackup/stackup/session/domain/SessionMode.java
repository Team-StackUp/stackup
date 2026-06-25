package com.stackup.stackup.session.domain;

public enum SessionMode {
    TECHNICAL,
    PERSONALITY,
    INTEGRATED,
    // 직무 맞춤: 회사명 + 채용공고(JD)를 받아 적합도·지원동기 질문 + 직무 적합도 피드백을 생성.
    JOB_TAILORED;

    public String koreanLabel() {
        return switch (this) {
            case TECHNICAL -> "기술 면접";
            case PERSONALITY -> "인성 면접";
            case INTEGRATED -> "종합 면접";
            case JOB_TAILORED -> "직무 맞춤 면접";
        };
    }

    public boolean isJobTailored() {
        return this == JOB_TAILORED;
    }
}
