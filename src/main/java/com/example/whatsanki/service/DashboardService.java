package com.example.whatsanki.service;

import com.example.whatsanki.dto.DashboardDto;

/** Aggregates owner-scoped counts and recent work for the browser dashboard. */
public interface DashboardService {
    DashboardDto getByGoogleSubject(String googleSubject);
}
