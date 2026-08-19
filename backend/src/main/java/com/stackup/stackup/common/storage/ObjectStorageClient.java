package com.stackup.stackup.common.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface ObjectStorageClient {

    StoredObject put(String key, InputStream content, long size, String contentType);

    InputStream get(String key);

    void delete(String key);

    URI createPresignedGetUrl(String key, Duration ttl);

    /**
     * 스토리지에 도달 가능한지 확인한다(헬스체크 전용). 실패하면 {@link StorageException}.
     * 키를 모르고도 확인할 수 있어야 해서 별도 메서드로 둔다 — get/put 은 대상 키가 필요하다.
     */
    void verifyAvailable();
}
