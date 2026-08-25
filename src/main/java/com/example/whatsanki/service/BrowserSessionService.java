package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserSessionDto;

/** Maps the authenticated provider identity to the browser session view. */
public interface BrowserSessionService {
    BrowserSessionDto getByGoogleSubject(String googleSubject);

    BrowserSessionDto updateInterfaceLanguage(String googleSubject, String interfaceLanguage);
}
