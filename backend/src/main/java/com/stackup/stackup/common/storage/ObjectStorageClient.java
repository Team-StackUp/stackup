package com.stackup.stackup.common.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface ObjectStorageClient {

    StoredObject put(String key, InputStream content, long size, String contentType);

    InputStream get(String key);

    void delete(String key);

    URI createPresignedGetUrl(String key, Duration ttl);
}
