package com.example.whatsanki.service;

import com.example.whatsanki.dto.*;

/** Accepts browser lookups and keeps result selection owner-scoped until a card is saved. */
public interface BrowserLookupService {
    BrowserLookupAcceptedDto create(String googleSubject, CreateBrowserLookupRequestDto request);
    BrowserLookupDto get(String googleSubject, String lookupReference);
    BrowserDeckOptionListDto decks(String googleSubject, String search, int page, int pageSize);
    BrowserLookupSaveDto save(String googleSubject, String lookupReference, int contextNumber, String deckReference);
}
