package com.stackup.stackup.common.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그에 남길 값에서 PII 를 가리는 유틸.
 *
 * <p><b>전역 로그 필터로 꽂지 말 것.</b> {@link #mask(String)} 를 Logback 컨버터 등으로 전
 * 로그에 적용하면 전화번호 패턴이 구분자 포함 9자리 이상 숫자열을 전부 잡아 <b>트레이스 ID 와
 * epoch millis 가 뭉개진다</b>:
 *
 * <pre>
 * traceId=01234567-89ab-cdef-0123-456789abcdef
 *   → traceId=***-***-6789ab-cdef-***-***-6789abcdef
 * </pre>
 *
 * <p>{@code X-Trace-Id} 상관관계는 Core·AI·RealTime 을 잇는 유일한 수단이라 이걸 잃는 대가가
 * 더 크다. 그래서 지금 호출부가 없다 — 없어서 빠뜨린 게 아니라 안 거는 쪽을 택한 것이다.
 *
 * <p>쓰는 방법: 값이 PII 임을 <b>아는</b> 지점에서 {@link #maskEmail}·{@link #maskPhoneNumber}
 * 같은 개별 함수를 직접 부른다. 애초에 본문을 로그에 남기지 않는 것이 1차 규약이다
 * ({@code docs/security.md §7}).
 */
public final class PiiMasker {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );
    private static final Pattern JWT_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"
    );
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile(
        "\\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]{20,}\\b"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?<!\\d)(?:\\+?\\d[\\d .-]{7,}\\d)(?!\\d)"
    );

    private PiiMasker() {
    }

    public static String maskEmail(String input) {
        if (input == null) {
            return null;
        }

        int atIndex = input.indexOf('@');
        if (atIndex <= 0) {
            return input;
        }

        String localPart = input.substring(0, atIndex);
        String domain = input.substring(atIndex);
        String visible = localPart.substring(0, Math.min(2, localPart.length()));
        return visible + "***" + domain;
    }

    public static String maskPhoneNumber(String input) {
        if (input == null) {
            return null;
        }

        String digits = input.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return input;
        }

        return "***-***-" + digits.substring(digits.length() - 4);
    }

    public static String maskJwt(String input) {
        if (input == null) {
            return null;
        }
        if (!input.contains(".")) {
            return input;
        }

        int visibleLength = Math.min(10, input.length());
        return input.substring(0, visibleLength) + "***";
    }

    public static String maskGithubToken(String input) {
        if (input == null) {
            return null;
        }

        int separatorIndex = input.indexOf('_');
        if (separatorIndex <= 0 || input.length() <= separatorIndex + 5) {
            return input;
        }

        String prefix = input.substring(0, Math.min(separatorIndex + 2, input.length()));
        String suffix = input.substring(input.length() - 4);
        return prefix + "***" + suffix;
    }

    public static String mask(String input) {
        if (input == null) {
            return null;
        }

        String masked = replaceMatches(input, EMAIL_PATTERN, PiiMasker::maskEmail);
        masked = replaceMatches(masked, JWT_PATTERN, PiiMasker::maskJwt);
        masked = replaceMatches(masked, GITHUB_TOKEN_PATTERN, PiiMasker::maskGithubToken);
        return replaceMatches(masked, PHONE_PATTERN, PiiMasker::maskPhoneNumber);
    }

    private static String replaceMatches(String input, Pattern pattern, MaskingFunction maskingFunction) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(maskingFunction.mask(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @FunctionalInterface
    private interface MaskingFunction {
        String mask(String input);
    }
}
