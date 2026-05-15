package com.stackup.stackup.common.storage;

public class StorageException extends RuntimeException {

    private final StorageErrorType type;

    public StorageException(StorageErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public StorageException(StorageErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public StorageErrorType getType() {
        return type;
    }
}
