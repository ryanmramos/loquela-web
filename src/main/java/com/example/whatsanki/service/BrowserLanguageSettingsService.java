package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserLanguageSettingsDto;

public interface BrowserLanguageSettingsService {

    BrowserLanguageSettingsDto getByGoogleSubject(String googleSubject);

    BrowserLanguageSettingsDto updateByGoogleSubject(
            String googleSubject,
            String sourceLanguage,
            String definitionLanguage
    );
}
