package com.example.whatsanki.service;

import com.example.whatsanki.dto.BrowserDeckDto;
import com.example.whatsanki.dto.BrowserExportDto;
import com.example.whatsanki.dto.DeckMutationRequestDto;

import java.net.URI;
import java.util.List;

public interface BrowserDeckService {
    List<BrowserDeckDto> list(String googleSubject);
    BrowserDeckDto get(String googleSubject, String reference);
    BrowserDeckDto create(String googleSubject, DeckMutationRequestDto request);
    BrowserDeckDto update(String googleSubject, String reference, DeckMutationRequestDto request);
    void delete(String googleSubject, String reference);
    BrowserExportDto export(String googleSubject, String deckReference);
    List<BrowserExportDto> exports(String googleSubject);
    BrowserExportDto exportStatus(String googleSubject, String exportReference);
    URI download(String googleSubject, String exportReference);
}
