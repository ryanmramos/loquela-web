package com.example.whatsanki.service;

import com.example.whatsanki.dto.PracticeRatingRequestDto;
import com.example.whatsanki.dto.PracticeSessionDto;

import java.util.Optional;

/** Runs the browser's immutable, non-scheduled practice sessions. */
public interface BrowserPracticeService {
    Optional<PracticeSessionDto> active(String googleSubject);
    PracticeSessionDto start(String googleSubject, String deckReference);
    PracticeSessionDto get(String googleSubject, String sessionReference);
    PracticeSessionDto reveal(String googleSubject, String sessionReference);
    PracticeSessionDto rate(
            String googleSubject, String sessionReference, PracticeRatingRequestDto request
    );
    PracticeSessionDto end(String googleSubject, String sessionReference);
}
