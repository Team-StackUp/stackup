package com.stackup.stackup.common.retry;

import java.util.Arrays;

public record RetryPolicy(long[] backoffMillis) {

    public RetryPolicy {
        if (backoffMillis == null) {
            throw new IllegalArgumentException("backoffMillis must not be null");
        }
        for (long delay : backoffMillis) {
            if (delay < 0) {
                throw new IllegalArgumentException("backoffMillis must be non-negative, got: " + delay);
            }
        }
        backoffMillis = backoffMillis.clone();
    }

    public static RetryPolicy of(long... backoffMillis) {
        return new RetryPolicy(backoffMillis);
    }

    public static RetryPolicy exponentialBackoff(long initialDelayMillis, int maxAttempts) {
        if (initialDelayMillis < 0) {
            throw new IllegalArgumentException("initialDelayMillis must be non-negative, got: " + initialDelayMillis);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got: " + maxAttempts);
        }
        if (maxAttempts > 50) {
            throw new IllegalArgumentException("maxAttempts must be at most 50 to avoid overflow, got: " + maxAttempts);
        }
        long[] backoffMillis = new long[maxAttempts - 1];
        for (int i = 0; i < backoffMillis.length; i++) {
            backoffMillis[i] = initialDelayMillis * (1L << i);
        }
        return new RetryPolicy(backoffMillis);
    }

    public int maxAttempts() {
        return backoffMillis.length + 1;
    }

    public long backoffForAttempt(int attempt) {
        return backoffMillis[attempt - 1];
    }

    @Override
    public long[] backoffMillis() {
        return backoffMillis.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RetryPolicy that && Arrays.equals(backoffMillis, that.backoffMillis);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(backoffMillis);
    }

    @Override
    public String toString() {
        return "RetryPolicy" + Arrays.toString(backoffMillis);
    }
}
