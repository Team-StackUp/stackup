package com.stackup.stackup.common.retry;

import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetryingExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryingExecutor.class);

    public void execute(String operationName, RetryPolicy policy, Runnable action) {
        execute(operationName, policy, () -> {
            action.run();
            return null;
        });
    }

    public <T> T execute(String operationName, RetryPolicy policy, Supplier<T> action) {
        try {
            return runWithRetry(operationName, policy, action);
        } catch (RetryInterruptedException interrupted) {
            log.error("Interrupted while retrying operation={}", operationName, interrupted);
            return null;
        } catch (RuntimeException terminalFailure) {
            log.error("Exhausted retries for operation={} (attempts={})",
                operationName, policy.maxAttempts(), terminalFailure);
            return null;
        }
    }

    public <T> T executeOrThrow(String operationName, RetryPolicy policy, Supplier<T> action) {
        return runWithRetry(operationName, policy, action);
    }

    private <T> T runWithRetry(String operationName, @NonNull RetryPolicy policy, Supplier<T> action) {
        int maxAttempts = policy.maxAttempts();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = action.get();
                if (attempt > 1) {
                    log.info("Succeeded operation={} on retry attempt={}", operationName, attempt);
                }
                return result;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (attempt == maxAttempts) {
                    break;
                }
                long backoff = policy.backoffForAttempt(attempt);
                log.warn("Operation failed name={} attempt={}/{}; retrying in {}ms",
                    operationName, attempt, maxAttempts, backoff, failure);
                if (!sleep(backoff)) {
                    throw new RetryInterruptedException(operationName, failure);
                }
            }
        }
        throw lastFailure;
    }

    // millis 만큼 sleep합니다
    // 테스트 코드에서 이 메서드를 오버라이드하여 sleep을 건너뛸 수 있습니다
    protected boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static final class RetryInterruptedException extends RuntimeException {
        public RetryInterruptedException(String operationName, Throwable cause) {
            super("Interrupted while retrying operation=" + operationName, cause);
        }
    }
}
