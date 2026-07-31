package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserSettingsDto;
import com.example.whatsanki.dto.UpdateBrowserSettingsRequestDto;

public interface BrowserSettingsService {
    BrowserSettingsDto get(String googleSubject);

    BrowserSettingsDto update(String googleSubject, UpdateBrowserSettingsRequestDto request);
}
