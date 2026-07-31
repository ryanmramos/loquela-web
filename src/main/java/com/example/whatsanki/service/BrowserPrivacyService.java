package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserAccountDeletionDto;

public interface BrowserPrivacyService {
    BrowserAccountDeletionDto deleteAccount(String googleSubject);
}
