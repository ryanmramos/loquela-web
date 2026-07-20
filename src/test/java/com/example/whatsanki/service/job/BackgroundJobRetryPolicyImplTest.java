package com.example.whatsanki.service.job;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackgroundJobRetryPolicyImplTest {

    @Test
    void appliesCappedExponentialDelayWithEqualJitter() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getJobs().setRetryBaseDelay(Duration.ofSeconds(10));
        configuration.getJobs().setRetryMaxDelay(Duration.ofSeconds(30));
        BackgroundJobRetryPolicyImpl policy = new BackgroundJobRetryPolicyImpl(configuration);

        assertThat(policy.retryDelay(1)).isBetween(Duration.ofSeconds(5), Duration.ofSeconds(10));
        assertThat(policy.retryDelay(2)).isBetween(Duration.ofSeconds(10), Duration.ofSeconds(20));
        assertThat(policy.retryDelay(10)).isBetween(Duration.ofSeconds(15), Duration.ofSeconds(30));
    }

    @Test
    void rejectsNonpositiveAttemptNumber() {
        BackgroundJobRetryPolicyImpl policy = new BackgroundJobRetryPolicyImpl(new WhatsAnkiConfigurationDto());

        assertThatThrownBy(() -> policy.retryDelay(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
