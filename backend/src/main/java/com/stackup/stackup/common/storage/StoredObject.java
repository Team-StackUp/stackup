package com.stackup.stackup.common.storage;

public record StoredObject(
    String bucket,
    String key,
    long size,
    String contentType
) {
}
