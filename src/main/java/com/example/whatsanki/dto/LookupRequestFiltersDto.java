package com.example.whatsanki.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stable, JSON-safe search scope captured when a website lookup is submitted. */
public record LookupRequestFiltersDto(Set<UUID> sourceIds, Set<String> sourceKinds) {
    public LookupRequestFiltersDto {
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        sourceKinds = sourceKinds == null ? Set.of() : Set.copyOf(sourceKinds);
    }
    public static LookupRequestFiltersDto none() { return new LookupRequestFiltersDto(Set.of(), Set.of()); }
    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceIds", sourceIds.stream().map(UUID::toString).sorted().toList());
        value.put("sourceKinds", sourceKinds.stream().sorted().toList());
        return Map.copyOf(value);
    }
    public static LookupRequestFiltersDto fromMap(Map<String, Object> value) {
        if (value == null) return none();
        Set<UUID> ids = value.get("sourceIds") instanceof List<?> list
                ? list.stream().map(Object::toString).map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
        Set<String> kinds = value.get("sourceKinds") instanceof List<?> list
                ? list.stream().map(Object::toString).collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
        return new LookupRequestFiltersDto(ids, kinds);
    }
}
