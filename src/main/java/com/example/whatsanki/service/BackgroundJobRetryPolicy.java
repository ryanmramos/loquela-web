package com.example.whatsanki.service;

import java.time.Duration;

public interface BackgroundJobRetryPolicy {
    Duration retryDelay(int completedAttempts);
}
