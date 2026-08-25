package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserSettingsDto;
import com.example.whatsanki.dto.UpdateBrowserSettingsRequestDto;

/** Reads and updates preferences used by future ingestion and lookup jobs. */
public interface BrowserSettingsService {
    BrowserSettingsDto get(String googleSubject);

    BrowserSettingsDto update(String googleSubject, UpdateBrowserSettingsRequestDto request);
}
