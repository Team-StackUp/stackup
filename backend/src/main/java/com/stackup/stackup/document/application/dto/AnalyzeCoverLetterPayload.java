package com.stackup.stackup.document.application.dto;

// 자소서는 파일(S3)이 아니라 문항을 합친 마크다운 본문을 inline 으로 실어 보낸다.
public record AnalyzeCoverLetterPayload(
    Long coverLetterId,
    String content,
    Long analyzedDocumentId
) {
}
