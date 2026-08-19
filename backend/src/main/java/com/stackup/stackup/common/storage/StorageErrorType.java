package com.stackup.stackup.common.storage;

public enum StorageErrorType {
    INVALID_OBJECT_KEY,
    UPLOAD_FAILED,
    DOWNLOAD_FAILED,
    DELETE_FAILED,
    PRESIGNED_URL_FAILED,
    // 헬스체크: 엔드포인트·자격증명·버킷 도달 실패.
    UNAVAILABLE
}
