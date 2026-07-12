package com.example.whatsanki.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record WhatsAppAddress(String value) {
    private static final Pattern VALID_ADDRESS = Pattern.compile("whatsapp:\\+[1-9][0-9]{7,14}");

    public WhatsAppAddress {
        if (value == null) throw new IllegalArgumentException("WhatsApp address is required");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!VALID_ADDRESS.matcher(value).matches()) {
            throw new IllegalArgumentException("WhatsApp address must use whatsapp:<E.164> format");
        }
    }
}
