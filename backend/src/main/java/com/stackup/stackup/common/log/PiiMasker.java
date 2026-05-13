package com.stackup.stackup.common.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
