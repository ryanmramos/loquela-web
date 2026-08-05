package com.example.whatsanki.service;

import com.example.whatsanki.dto.*;

import java.net.URI;
public interface BrowserDeckService {
    BrowserDeckListDto list(String googleSubject, String search, int page, int pageSize);
    BrowserDeckDto get(String googleSubject, String reference);
    BrowserDeckDto create(String googleSubject, DeckMutationRequestDto request);
    BrowserDeckDto update(String googleSubject, String reference, DeckMutationRequestDto request);
    void delete(String googleSubject, String reference);
    BrowserExportDto export(String googleSubject, String deckReference);
    BrowserExportListDto exports(String googleSubject, int page, int pageSize);
    BrowserExportDto exportStatus(String googleSubject, String exportReference);
    URI download(String googleSubject, String exportReference);
}
