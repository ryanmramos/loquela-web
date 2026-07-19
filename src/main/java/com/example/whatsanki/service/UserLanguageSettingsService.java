package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LanguagePairUpdateResultDto;

public interface UserLanguageSettingsService {

    LanguagePairDto getCurrent(UserId userId);

    LanguagePairUpdateResultDto update(UserId userId, String sourceLanguage, String definitionLanguage);
}
