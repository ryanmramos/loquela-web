package com.example.whatsanki.service.job;

import com.example.whatsanki.dto.ClaimedBackgroundJobDto;

/** Selects the unique handler registered for a claimed job type. */
public interface BackgroundJobDispatcher {
    void dispatch(ClaimedBackgroundJobDto job);
}
