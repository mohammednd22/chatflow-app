
package com.chatflow;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreaker {
    private enum State {
        CLOSED,    // Normal operation
        OPEN,      // Failing, reject requests
        HALF_OPEN  // Testing if recovered
    }

    private static final int FAILURE_THRESHOLD = 10;
    private static final long TIMEOUT_MS = 10000; // 10 seconds
    private static final int SUCCESS_THRESHOLD = 5;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime.get() >= TIMEOUT_MS) {
                state = State.HALF_OPEN;
                successCount.set(0);
                return true;
            }
            return false;
        }
        return true;
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= SUCCESS_THRESHOLD) {
                state = State.CLOSED;
                failureCount.set(0);
            }
        } else if (state == State.CLOSED) {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            failureCount.set(0);
        } else if (failureCount.incrementAndGet() >= FAILURE_THRESHOLD) {
            state = State.OPEN;
        }
    }

    public State getState() {
        return state;
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }
}