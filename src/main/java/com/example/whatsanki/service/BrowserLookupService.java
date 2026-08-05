package com.example.whatsanki.service;

import com.example.whatsanki.dto.*;
public interface BrowserLookupService {
    BrowserLookupAcceptedDto create(String googleSubject, CreateBrowserLookupRequestDto request);
    BrowserLookupDto get(String googleSubject, String lookupReference);
    BrowserDeckOptionListDto decks(String googleSubject, String search, int page, int pageSize);
    BrowserLookupSaveDto save(String googleSubject, String lookupReference, int contextNumber, String deckReference);
}
