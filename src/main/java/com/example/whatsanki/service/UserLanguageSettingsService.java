package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;

/** Stores the language pair that new work captures at acceptance time. */
public interface UserLanguageSettingsService {

    LanguagePairDto getCurrent(UserId userId);

    LanguagePairUpdateResultDto update(UserId userId, String sourceLanguage, String definitionLanguage);
}
