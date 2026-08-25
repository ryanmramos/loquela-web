package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;

/** Processes one claimed job type without owning the lease transaction. */
public interface BackgroundJobHandler {
    BackgroundJobEntity.Type supportedType();

    void handle(ClaimedBackgroundJobDto job);
}
