package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.LanguagePairDto;

public interface UserLanguageSettingsService {

    LanguagePairDto getCurrent(UserId userId);
}
