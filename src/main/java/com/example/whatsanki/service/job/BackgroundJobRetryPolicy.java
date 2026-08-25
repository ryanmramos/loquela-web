package com.example.whatsanki.service.job;

import java.time.Duration;

/** Computes bounded retry delays for transient worker failures. */
public interface BackgroundJobRetryPolicy {
    Duration retryDelay(int completedAttempts);
}
