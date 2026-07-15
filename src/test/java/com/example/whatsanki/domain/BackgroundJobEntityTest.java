package com.example.whatsanki.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundJobEntityTest {

    @Test
    void allowsOnlyGuardedLifecycleTransitions() {
        assertThat(BackgroundJobEntity.State.QUEUED.canTransitionTo(BackgroundJobEntity.State.RUNNING)).isTrue();
        assertThat(BackgroundJobEntity.State.RUNNING.canTransitionTo(BackgroundJobEntity.State.RETRY_WAIT)).isTrue();
        assertThat(BackgroundJobEntity.State.RUNNING.canTransitionTo(BackgroundJobEntity.State.SUCCEEDED)).isTrue();
        assertThat(BackgroundJobEntity.State.RETRY_WAIT.canTransitionTo(BackgroundJobEntity.State.RUNNING)).isTrue();
        assertThat(BackgroundJobEntity.State.SUCCEEDED.canTransitionTo(BackgroundJobEntity.State.RUNNING)).isFalse();
        assertThat(BackgroundJobEntity.State.DEAD.canTransitionTo(BackgroundJobEntity.State.QUEUED)).isFalse();
    }
}
