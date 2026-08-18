package com.stackup.stackup.resume.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 사용자가 준 URL 을 AI 서버가 그대로 fetch 하므로 SSRF 검증이 필수다.
 *
 * <p>AI 서버는 docker 네트워크 안에서 Core·PostgreSQL·RabbitMQ·MinIO 에 닿을 수 있고, 배포 호스트에서는
 * 클라우드 메타데이터 엔드포인트(169.254.169.254)에도 닿는다. 검증 없이 넘기면 사용자가
 * {@code http://minio:9000/...} 이나 {@code http://169.254.169.254/latest/meta-data/} 를 "포트폴리오"로
 * 등록해 내부 응답을 요약문으로 돌려받을 수 있다.
 *
 * <p>여기서 호스트를 resolve 해 사설/루프백 대역을 막지만, DNS rebinding 과 리다이렉트로 우회할 수 있다.
 * 실제 소켓을 여는 AI 서버(WebSourceExtractor)에도 같은 검사가 있어야 완결된다 — 이 클래스는 첫 번째 관문이다.
 */
@Component
public class WebResumeUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(WebResumeUrlValidator.class);

    // resumes.source_url 컬럼 길이.
    private static final int MAX_URL_LENGTH = 2000;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /** 호스트 → 주소 해석. 테스트에서 실제 DNS 를 타지 않도록 분리한다. */
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver resolver;

    // Spring 은 다른 생성자에 @Autowired 가 없으면 기본 생성자를 쓴다.
    public WebResumeUrlValidator() {
        this(InetAddress::getAllByName);
    }

    WebResumeUrlValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /** 정규화된 URI 를 반환한다. 부적합하면 {@link DomainException}(RESUME_INVALID_URL). */
    public URI validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw reject("URL 을 입력해 주세요.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw reject("URL 이 너무 깁니다. (최대 %d자)".formatted(MAX_URL_LENGTH));
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw reject("URL 형식이 올바르지 않습니다.");
        }
        if (!uri.isAbsolute()) {
            throw reject("http:// 또는 https:// 로 시작하는 전체 주소를 입력해 주세요.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw reject("http, https 주소만 등록할 수 있습니다.");
        }
        // user:pass@host 는 리다이렉트/파서 차이를 이용한 호스트 위장에 쓰인다.
        if (uri.getUserInfo() != null) {
            throw reject("사용자 정보가 포함된 URL 은 등록할 수 없습니다.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw reject("URL 에서 호스트를 찾을 수 없습니다.");
        }
        requirePublicHost(host);
        return uri;
    }

    private void requirePublicHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw reject("주소를 찾을 수 없는 호스트입니다: %s".formatted(host));
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                // 어떤 내부 주소로 해석됐는지는 응답에 노출하지 않는다(내부 토폴로지 추측 방지).
                log.warn("web resume URL rejected — non-public address. host={}, resolved={}",
                    host, address.getHostAddress());
                throw reject("내부 네트워크 주소는 등록할 수 없습니다.");
            }
        }
    }

    // 루프백·사설(RFC1918)·링크로컬(169.254/16, 클라우드 메타데이터 포함)·멀티캐스트·
    // 와일드카드(0.0.0.0)·IPv6 unique-local 을 모두 막는다.
    private boolean isBlocked(InetAddress address) {
        return address.isLoopbackAddress()
            || address.isAnyLocalAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()
            || isUniqueLocalIpv6(address);
    }

    // fc00::/7 — isSiteLocalAddress() 가 IPv6 에서는 deprecated 인 fec0::/10 만 보므로 별도 확인.
    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private DomainException reject(String message) {
        return new DomainException(ApiErrorCode.RESUME_INVALID_URL, message);
    }
}
