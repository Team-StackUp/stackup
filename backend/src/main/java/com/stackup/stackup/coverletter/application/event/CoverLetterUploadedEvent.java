package com.stackup.stackup.coverletter.application.event;

// 자소서 생성 직후 발행. document 도메인이 listener 로 받아 분석 트리거.
// coverletter 도메인이 document 도메인을 직접 의존하지 않도록 분리하기 위한 매개체.
public record CoverLetterUploadedEvent(
    Long userId,
    Long coverLetterId
) {
}
