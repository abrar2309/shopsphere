package com.abrar.shopsphere.dto;

public class RateLimitResult {

    private final boolean allowed;
    private final long limit;
    private final long used;
    private final long remaining;
    private final long retryAfterSeconds;

    public RateLimitResult(
            boolean allowed,
            long limit,
            long used,
            long remaining,
            long retryAfterSeconds) {

        this.allowed = allowed;
        this.limit = limit;
        this.used = used;
        this.remaining = remaining;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getLimit() {
        return limit;
    }

    public long getUsed() {
        return used;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}