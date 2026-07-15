package com.example.whatsanki.service;

import com.example.whatsanki.dto.ClaimedBackgroundJobDto;

public interface BackgroundJobDispatcher {
    void dispatch(ClaimedBackgroundJobDto job);
}
