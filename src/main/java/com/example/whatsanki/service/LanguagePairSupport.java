package com.example.whatsanki.service;

import com.example.whatsanki.dto.LanguagePairDto;

import java.util.List;

/**
 * Product-enabled language-pair boundary. Implementations must ensure this list is a
 * valid subset of the active dictionary provider's advertised capabilities.
 */
public interface LanguagePairSupport {

    boolean supports(LanguagePairDto languagePair);

    List<LanguagePairDto> supportedPairs();
}
