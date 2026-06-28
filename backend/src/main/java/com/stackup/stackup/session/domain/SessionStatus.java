package com.stackup.stackup.session.domain;

public enum SessionStatus {
    READY,
    IN_PROGRESS,
    INTERRUPTED,
    COMPLETED,
    CANCELLED;

    // 더 이상 진행/전이가 불가능한 종료 상태. 종료 후 도착하는 비동기 콜백을 드롭하는 가드에 사용.
    public boolean isTerminal() {
        return this == INTERRUPTED || this == COMPLETED || this == CANCELLED;
    }
}
