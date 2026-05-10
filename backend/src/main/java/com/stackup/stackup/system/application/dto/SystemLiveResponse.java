package com.stackup.stackup.system.application.dto;

import java.time.Instant;

public record SystemLiveResponse(
    String status,
    Instant timestamp
) {
}
