package com.stackup.stackup.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.common.response.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerMaxUploadSizeTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void max_upload_size_exception_maps_to_resume_file_too_large() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(20L * 1024 * 1024);

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSizeExceeded(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo(ApiErrorCode.RESUME_FILE_TOO_LARGE.name());
        assertThat(body.details()).containsEntry("maxBytes", 20L * 1024 * 1024);
    }
}
