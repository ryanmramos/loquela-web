package com.example.whatsanki.service;

import com.example.whatsanki.dto.PastedTextSourceDto;

public interface BrowserSourceService {
    PastedTextSourceDto createPastedText(String googleSubject, String text);
}
