package com.example.whatsanki.service;

import com.example.whatsanki.dto.*;
import java.util.List;

public interface BrowserLookupService {
    BrowserLookupAcceptedDto create(String googleSubject, CreateBrowserLookupRequestDto request);
    BrowserLookupDto get(String googleSubject, String lookupReference);
    List<BrowserDeckOptionDto> decks(String googleSubject);
    BrowserLookupSaveDto save(String googleSubject, String lookupReference, int contextNumber, String deckReference);
}
