package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserAccountDeletionDto;
import com.example.whatsanki.dto.BrowserAccountDeletionStatusDto;

public interface BrowserPrivacyService {
    BrowserAccountDeletionDto deleteAccount(String googleSubject);
    BrowserAccountDeletionStatusDto currentDeletion(String googleSubject);
}
