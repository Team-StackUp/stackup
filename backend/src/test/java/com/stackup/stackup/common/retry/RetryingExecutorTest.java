package com.stackup.stackup.common.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingExecutorTest {

    private final List<Long> sleeps = new ArrayList<>();

    private final RetryingExecutor executor = new RetryingExecutor() {
        @Override
        protected boolean sleep(long millis) {
            sleeps.add(millis);
            return true;
        }
    };

    @Test
    void executes_once_when_action_succeeds() {
        int[] calls = {0};

        executor.execute("op", RetryPolicy.of(100L, 200L), () -> calls[0]++);

        assertThat(calls[0]).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retries_then_succeeds_on_second_attempt() {
        int[] calls = {0};

        executor.execute("op", RetryPolicy.of(50L, 100L), () -> {
            calls[0]++;
            if (calls[0] == 1) {
                throw new IllegalStateException("first attempt fails");
            }
        });

        assertThat(calls[0]).isEqualTo(2);
        assertThat(sleeps).containsExactly(50L);
    }

    @Test
    void swallows_exception_after_exhausting_attempts() {
        int[] calls = {0};

        executor.execute("op", RetryPolicy.of(10L, 20L), () -> {
            calls[0]++;
            throw new IllegalStateException("always fails");
        });

        assertThat(calls[0]).isEqualTo(3);
        assertThat(sleeps).containsExactly(10L, 20L);
    }

    @Test
    void executeOrThrow_propagates_final_failure() {
        int[] calls = {0};

        assertThatThrownBy(() -> executor.executeOrThrow("op", RetryPolicy.of(1L), () -> {
            calls[0]++;
            throw new IllegalStateException("nope");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("nope");
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    void executeOrThrow_returns_value_on_success() {
        Integer result = executor.executeOrThrow("op", RetryPolicy.of(1L), () -> 42);

        assertThat(result).isEqualTo(42);
    }

    @Test
    void interrupt_during_backoff_stops_retry_and_swallows() {
        int[] calls = {0};
        RetryingExecutor interruptingExecutor = new RetryingExecutor() {
            @Override
            protected boolean sleep(long millis) {
                return false;
            }
        };

        interruptingExecutor.execute("op", RetryPolicy.of(50L, 100L), () -> {
            calls[0]++;
            throw new IllegalStateException("fail");
        });

        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    void zero_backoff_policy_means_immediate_retry() {
        RetryPolicy immediate = RetryPolicy.of(0L, 0L);
        int[] calls = {0};

        executor.execute("op", immediate, () -> {
            calls[0]++;
            if (calls[0] < 3) {
                throw new IllegalStateException("retry");
            }
        });

        assertThat(calls[0]).isEqualTo(3);
        assertThat(sleeps).containsExactly(0L, 0L);
    }

    @Test
    void policy_rejects_negative_backoff() {
        assertThatThrownBy(() -> RetryPolicy.of(100L, -1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policy_max_attempts_equals_backoff_length_plus_one() {
        assertThat(RetryPolicy.of().maxAttempts()).isEqualTo(1);
        assertThat(RetryPolicy.of(1L).maxAttempts()).isEqualTo(2);
        assertThat(RetryPolicy.of(1L, 2L, 3L).maxAttempts()).isEqualTo(4);
    }
}
