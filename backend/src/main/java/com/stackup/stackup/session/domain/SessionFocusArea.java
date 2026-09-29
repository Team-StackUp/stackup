package com.stackup.stackup.session.domain;

// 약점 집중 재도전에서 겨냥할 평가 축. session_feedbacks 의 점수 축과 1:1.
public enum SessionFocusArea {
    // technical_accuracy — 직무 역량(개발 직군이면 기술 정확도). 컬럼명은 하위호환으로 유지.
    TECHNICAL,
    // logic_score — 논리력/구조
    LOGIC,
    // communication_score — 전달력
    COMMUNICATION
}
