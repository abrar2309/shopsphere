package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.RateLimitResult;
import com.abrar.shopsphere.exception.RateLimitExceededException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(
            StringRedisTemplate redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult checkRateLimit(
            String key,
            int limit,
            Duration window) {

        /*
         * Atomic Redis operation.
         *
         * INCR and EXPIRE execute together
         * inside Redis.
         */
        String script = """
                local count = redis.call('INCR', KEYS[1])

                if count == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end

                return count
                """;

        DefaultRedisScript<Long> redisScript =
                new DefaultRedisScript<>();

        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);

        Long count =
                redisTemplate.execute(
                        redisScript,
                        Collections.singletonList(key),
                        String.valueOf(
                                window.getSeconds()
                        )
                );

        if (count == null) {

            throw new IllegalStateException(
                    "Unable to update rate limit counter."
            );
        }

        long remaining =
                Math.max(
                        0,
                        limit - count
                );

        /*
         * Request exceeded the limit.
         */
        if (count > limit) {

            Long ttl =
                    redisTemplate.getExpire(
                            key,
                            TimeUnit.SECONDS
                    );

            long retryAfterSeconds = 0;

            if (ttl != null && ttl > 0) {
                retryAfterSeconds = ttl;
            }

            throw new RateLimitExceededException(
                    "Too many requests. Please try again later.",
                    limit,
                    0,
                    retryAfterSeconds
            );
        }

        /*
         * Request is allowed.
         */
        return new RateLimitResult(
                true,
                limit,
                count,
                remaining,
                0
        );
    }
}