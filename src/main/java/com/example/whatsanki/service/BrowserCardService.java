package com.example.whatsanki.service;
import com.example.whatsanki.dto.*;

/** Provides owner-scoped card CRUD for the browser API. */
public interface BrowserCardService {
    BrowserCardListDto list(String subject, int page, int pageSize, String search, String deckReference);
    BrowserCardDto get(String subject, String reference);
    BrowserCardDto create(String subject, CardMutationRequestDto request);
    BrowserCardDto update(String subject, String reference, CardMutationRequestDto request);
    void delete(String subject, String reference);
}
