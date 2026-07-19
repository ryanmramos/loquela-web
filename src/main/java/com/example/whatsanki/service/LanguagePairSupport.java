package com.example.whatsanki.service;

import com.example.whatsanki.dto.LanguagePairDto;

import java.util.List;

/**
 * Capability boundary for language pairs supported by the active dictionary provider.
 */
public interface LanguagePairSupport {

    boolean supports(LanguagePairDto languagePair);

    List<LanguagePairDto> supportedPairs();
}
