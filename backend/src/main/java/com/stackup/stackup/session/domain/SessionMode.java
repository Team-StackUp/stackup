package com.stackup.stackup.session.domain;

public enum SessionMode {
    TECHNICAL,
    PERSONALITY,
    INTEGRATED;

    public String koreanLabel() {
        return switch (this) {
            case TECHNICAL -> "기술 면접";
            case PERSONALITY -> "인성 면접";
            case INTEGRATED -> "종합 면접";
        };
    }
}
