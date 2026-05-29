package com.stackup.stackup.session.presentation.dto;

public record SessionEndRequest(Boolean cancel) {
    public boolean isCancel() { return Boolean.TRUE.equals(cancel); }
}
