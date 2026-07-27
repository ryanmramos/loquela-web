package com.example.whatsanki.service;

import com.example.whatsanki.dto.DashboardDto;

public interface DashboardService {
    DashboardDto getByGoogleSubject(String googleSubject);
}
