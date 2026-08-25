package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserLanguageSettingsDto;

/** Reads and updates the language pair captured for future work. */
public interface BrowserLanguageSettingsService {

    BrowserLanguageSettingsDto getByGoogleSubject(String googleSubject);

    BrowserLanguageSettingsDto updateByGoogleSubject(
            String googleSubject,
            String sourceLanguage,
            String definitionLanguage
    );
}
