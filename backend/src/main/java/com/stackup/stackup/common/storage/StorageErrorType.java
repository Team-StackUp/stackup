package com.stackup.stackup.common.storage;

public enum StorageErrorType {
    INVALID_OBJECT_KEY,
    // 키는 유효하나 객체가 없음(NoSuchKey) — 호출부가 인프라 장애(503)와 구분해 처리할 수 있게 분리.
    OBJECT_NOT_FOUND,
    UPLOAD_FAILED,
    DOWNLOAD_FAILED,
    DELETE_FAILED,
    PRESIGNED_URL_FAILED,
    // 헬스체크: 엔드포인트·자격증명·버킷 도달 실패.
    UNAVAILABLE
}
