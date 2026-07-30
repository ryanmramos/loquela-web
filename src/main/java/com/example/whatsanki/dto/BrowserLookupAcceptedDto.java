package com.example.whatsanki.dto;
import java.time.Instant;
public record BrowserLookupAcceptedDto(String reference, Instant expiresAt) { }
