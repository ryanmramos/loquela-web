package com.example.whatsanki.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record SaveLookupResultRequestDto(@NotBlank @Size(min = 22, max = 22) String deckReference) { }
