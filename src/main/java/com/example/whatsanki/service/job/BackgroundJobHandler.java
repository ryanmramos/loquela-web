package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;

public interface BackgroundJobHandler {
    BackgroundJobEntity.Type supportedType();

    void handle(ClaimedBackgroundJobDto job);
}
