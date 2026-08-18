package com.stackup.stackup.resume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SSRF 가드. AI 서버가 이 URL 을 그대로 fetch 하므로 내부망 접근이 뚫리면 안 된다.
 *
 * <p>DNS 는 주입한 fake 로 해석한다 — 실제 조회를 타면 테스트가 네트워크에 의존해 CI 에서 흔들린다.
 * IP 리터럴은 fake 없이도 파싱되므로 대역 판정은 실제 {@link InetAddress} 로 검증된다.
 */
class WebResumeUrlValidatorTest {

    // 도메인 → 해석 결과. 없는 이름은 UnknownHostException.
    private static final Map<String, String> DNS = Map.of(
        "example.com", "93.184.216.34",
        "blog.example.com", "93.184.216.34",
        "internal.example.com", "10.1.2.3"        // 공개 도메인이 사설 IP 로 해석되는 경우
    );

    private final WebResumeUrlValidator validator = new WebResumeUrlValidator(host -> {
        String mapped = DNS.get(host);
        if (mapped != null) {
            return new InetAddress[] {InetAddress.getByName(mapped)};
        }
        // IP 리터럴은 그대로 파싱(DNS 조회 없음), 그 외 미등록 이름은 해석 실패.
        if (host.matches("[0-9.]+") || host.startsWith("[") || host.contains(":")) {
            return new InetAddress[] {InetAddress.getByName(host)};
        }
        if (host.equals("localhost")) {
            return new InetAddress[] {InetAddress.getByName("127.0.0.1")};
        }
        throw new UnknownHostException(host);
    });

    @Test
    void acceptsPublicHttpsUrl() {
        URI uri = validator.validate("https://example.com/portfolio");

        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.toString()).isEqualTo("https://example.com/portfolio");
    }

    @Test
    void trimsWhitespace() {
        assertThat(validator.validate("  https://example.com/me  ").toString())
            .isEqualTo("https://example.com/me");
    }

    // 루프백·사설·링크로컬(클라우드 메타데이터)·와일드카드 — AI 컨테이너가 닿을 수 있는 대역 전부.
    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:8080/api/internal/documents/1",
        "http://localhost:9000/stackup",
        "http://169.254.169.254/latest/meta-data/",
        "http://10.0.0.5/",
        "http://172.16.0.1/",
        "http://192.168.1.1/",
        "http://0.0.0.0/",
    })
    void rejectsNonPublicAddresses(String url) {
        assertRejected(url);
    }

    // 공개 도메인이라도 해석 결과가 사설이면 막는다 — 이름만 보고 판단하면 뚫린다.
    @Test
    void rejectsPublicHostnameResolvingToPrivateAddress() {
        assertRejected("https://internal.example.com/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "file:///etc/passwd",
        "gopher://example.com/",
        "ftp://example.com/resume.pdf",
        "javascript:alert(1)",
        "//example.com/no-scheme",
        "example.com",
        "not a url at all",
    })
    void rejectsUnsupportedSchemes(String url) {
        assertRejected(url);
    }

    // user:pass@ 는 파서 차이를 이용한 호스트 위장에 쓰인다.
    @Test
    void rejectsUserInfo() {
        assertRejected("https://evil.example.com@example.com/");
    }

    @Test
    void rejectsBlankAndOverlongUrl() {
        assertRejected(null);
        assertRejected("   ");
        assertRejected("https://example.com/" + "a".repeat(2000));
    }

    @Test
    void rejectsUnresolvableHost() {
        assertRejected("https://this-host-should-not-resolve.invalid/");
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> validator.validate(url))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.RESUME_INVALID_URL));
    }
}
