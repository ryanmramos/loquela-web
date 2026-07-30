package com.example.whatsanki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public record CreateBrowserLookupRequestDto(
        @NotBlank @Size(max = 256) String term,
        @Size(max = 50) List<@Size(min = 22, max = 22) String> sourceReferences,
        @Size(max = 5) Set<@Size(max = 32) String> sourceKinds
) { }
