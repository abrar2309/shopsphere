package com.abrar.shopsphere.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long limit;
    private final long remaining;
    private final long retryAfterSeconds;

    public RateLimitExceededException(
            String message,
            long limit,
            long remaining,
            long retryAfterSeconds) {

        super(message);

        this.limit = limit;
        this.remaining = remaining;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getLimit() {
        return limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}