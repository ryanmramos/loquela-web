package com.example.whatsanki.service.exception;

import com.example.whatsanki.dto.LanguagePairDto;

/** Permanent provider-contract failure for a pair outside provider capabilities. */
public class UnsupportedDictionaryLanguagePairException extends IllegalArgumentException {

    private final String providerId;
    private final LanguagePairDto languagePair;

    public UnsupportedDictionaryLanguagePairException(String providerId, LanguagePairDto languagePair) {
        super("Dictionary provider " + providerId + " does not support language pair "
                + languagePair.sourceLanguage() + ":" + languagePair.definitionLanguage());
        this.providerId = providerId;
        this.languagePair = languagePair;
    }

    public String getProviderId() {
        return providerId;
    }

    public LanguagePairDto getLanguagePair() {
        return languagePair;
    }
}
