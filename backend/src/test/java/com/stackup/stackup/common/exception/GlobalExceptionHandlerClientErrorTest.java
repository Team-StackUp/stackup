package com.stackup.stackup.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.common.response.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 잘못된 요청이 500 으로 새어 나가면 운영 로그에서 진짜 장애를 구분할 수 없다.
// 실제로 운영 로그의 SYS_INTERNAL_ERROR 11건이 전부 아래 네 종류였고 서버 버그는 0건이었다.
class GlobalExceptionHandlerClientErrorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 본문이_없거나_깨진_JSON_은_400() {
        var exception = new HttpMessageNotReadableException(
            "Required request body is missing", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableBody(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void 파싱_실패_응답에_사용자_입력이_섞이지_않는다() {
        // 원문 메시지에는 깨진 본문 조각이 들어온다 — 그대로 돌려주면 입력 반사가 된다.
        var exception = new HttpMessageNotReadableException(
            "JSON parse error: <script>alert(1)</script>", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableBody(exception);

        assertThat(response.getBody().message()).doesNotContain("script");
        assertThat(response.getBody().message())
            .isEqualTo(ApiErrorCode.MALFORMED_REQUEST.getDefaultMessage());
    }

    @Test
    void 경로변수_타입_불일치는_400_이고_파라미터명을_알려준다() {
        var exception = new MethodArgumentTypeMismatchException(
            "abc", Long.class, "sessionId", null, new NumberFormatException());

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(response.getBody().details()).containsEntry("parameter", "sessionId");
    }

    @Test
    void 없는_경로는_404() {
        // 봇 스캔이 상시 들어온다 — 500 으로 두면 에러 로그가 통째로 오염된다.
        var exception = new NoResourceFoundException(HttpMethod.GET, "/api/nope", "/api/nope");

        ResponseEntity<ApiErrorResponse> response = handler.handleNoResource(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ENDPOINT_NOT_FOUND");
    }

    @Test
    void 지원하지_않는_메서드는_405() {
        var exception = new HttpRequestMethodNotSupportedException("TRACE");

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodNotSupported(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void 네_코드_모두_4xx_다() {
        // 5xx 로 돌아가면 클라이언트 재시도 로직이 영원히 성공하지 못할 요청을 반복한다.
        for (ApiErrorCode code : new ApiErrorCode[] {
            ApiErrorCode.MALFORMED_REQUEST,
            ApiErrorCode.ENDPOINT_NOT_FOUND,
            ApiErrorCode.METHOD_NOT_ALLOWED,
            ApiErrorCode.VALIDATION_ERROR,
        }) {
            assertThat(code.getStatus().is4xxClientError())
                .as("%s 는 4xx 여야 한다", code)
                .isTrue();
        }
    }
}
