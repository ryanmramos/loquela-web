package com.example.whatsanki.service.job;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class BackgroundJobRetryPolicyImpl implements BackgroundJobRetryPolicy {

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public Duration retryDelay(int completedAttempts) {
        if (completedAttempts < 1) {
            throw new IllegalArgumentException("completedAttempts must be positive");
        }

        long baseMillis = configuration.getJobs().getRetryBaseDelay().toMillis();
        long maximumMillis = configuration.getJobs().getRetryMaxDelay().toMillis();
        if (baseMillis < 1 || maximumMillis < baseMillis) {
            throw new IllegalStateException("Job retry delays must be positive and ordered");
        }

        int exponent = Math.min(completedAttempts - 1, 30);
        long exponentialMillis;
        try {
            exponentialMillis = Math.multiplyExact(baseMillis, 1L << exponent);
        } catch (ArithmeticException exception) {
            exponentialMillis = maximumMillis;
        }
        long cappedMillis = Math.min(exponentialMillis, maximumMillis);

        // Equal jitter avoids synchronized retry storms while retaining a useful
        // minimum delay. The returned value is always between half and all of the cap.
        long lowerBound = Math.max(1L, cappedMillis / 2L);
        long jitteredMillis = lowerBound == cappedMillis
                ? cappedMillis
                : ThreadLocalRandom.current().nextLong(lowerBound, cappedMillis + 1L);
        return Duration.ofMillis(jitteredMillis);
    }
}
