package com.example.whatsanki.service.job;

import java.time.Duration;

public interface BackgroundJobRetryPolicy {
    Duration retryDelay(int completedAttempts);
}
