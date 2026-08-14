package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.service.exception.DemoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class DemoRateLimiter {
    private static final int MAX_BUCKETS = 10_000;
    private final byte[] salt = new byte[32];
    private final Map<String, ArrayDeque<Instant>> buckets = new ConcurrentHashMap<>();

    DemoRateLimiter() {
        new SecureRandom().nextBytes(salt);
    }

    void require(HttpServletRequest request, String operation, int maximum, Duration window) {
        String key = operation + ':' + hashedIp(request.getRemoteAddr());
        if (!buckets.containsKey(key) && buckets.size() >= MAX_BUCKETS) {
            throw new DemoException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_BUSY", 60, null);
        }
        Instant now = Instant.now();
        ArrayDeque<Instant> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant cutoff = now.minus(window);
            while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) bucket.removeFirst();
            if (bucket.size() >= maximum) {
                int retryAfter = Math.max(1, (int) Duration.between(now,
                        bucket.peekFirst().plus(window)).toSeconds());
                throw new DemoException(HttpStatus.TOO_MANY_REQUESTS, "DEMO_RATE_LIMITED", retryAfter, null);
            }
            bucket.addLast(now);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void discardIdleBuckets() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        buckets.entrySet().removeIf(entry -> {
            ArrayDeque<Instant> bucket = entry.getValue();
            synchronized (bucket) {
                while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) bucket.removeFirst();
                return bucket.isEmpty();
            }
        });
    }

    private String hashedIp(String ip) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    String.valueOf(ip).getBytes(StandardCharsets.UTF_8)), 0, 12);
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }
}
