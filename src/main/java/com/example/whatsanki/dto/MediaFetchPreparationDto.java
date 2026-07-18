package com.example.whatsanki.dto;

import java.net.URI;

public record MediaFetchPreparationDto(
        boolean alreadyStored,
        URI mediaUri,
        String declaredMediaType
) {
    public static MediaFetchPreparationDto stored() {
        return new MediaFetchPreparationDto(true, null, null);
    }
}
