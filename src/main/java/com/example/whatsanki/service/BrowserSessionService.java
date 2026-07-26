package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserSessionDto;

public interface BrowserSessionService {
    BrowserSessionDto getByGoogleSubject(String googleSubject);
}
