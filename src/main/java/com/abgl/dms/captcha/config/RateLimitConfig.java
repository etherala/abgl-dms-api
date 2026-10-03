package com.abgl.dms.captcha.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class RateLimitConfig {

    private final ConcurrentMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Bean
    public BucketFactory bucketFactory(
            @Value("${captcha.rate-limit.enabled:true}") boolean enabled,
            @Value("${captcha.rate-limit.capacity:5}") int capacity,
            @Value("${captcha.rate-limit.refill-tokens:5}") int refillTokens,
            @Value("${captcha.rate-limit.refill-seconds:60}") long refillSeconds) {
        return new BucketFactory(enabled, capacity, refillTokens, refillSeconds, limiters);
    }

    public static class BucketFactory {
        private final boolean enabled;
        private final int capacity;
        private final int refillTokens;
        private final long refillSeconds;
        private final ConcurrentMap<String, RateLimiter> limiters;

        public BucketFactory(boolean enabled, int capacity, int refillTokens, long refillSeconds,
                            ConcurrentMap<String, RateLimiter> limiters) {
            this.enabled = enabled;
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillSeconds = refillSeconds;
            this.limiters = limiters;
        }

        public boolean tryConsume(String key) {
            if (!enabled) {
                return true;
            }
            RateLimiter limiter = limiters.computeIfAbsent(key,
                    ignored -> RateLimiter.create((double) refillTokens / refillSeconds));
            return limiter.tryAcquire();
        }
    }
}
