package com.stackup.stackup.session.domain;

public enum JobCategory {
    FRONTEND,
    BACKEND,
    INFRA,
    DBA;

    public String koreanLabel() {
        return switch (this) {
            case FRONTEND -> "프론트엔드";
            case BACKEND -> "백엔드";
            case INFRA -> "인프라";
            case DBA -> "DBA";
        };
    }
}
