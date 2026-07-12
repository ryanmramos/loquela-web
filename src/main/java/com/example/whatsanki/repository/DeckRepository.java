package com.example.whatsanki.repository;

import com.example.whatsanki.domain.DeckEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends Repository<DeckEntity, UUID> {
    DeckEntity save(DeckEntity deck);
    Optional<DeckEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<DeckEntity> findByUserIdAndNormalizedName(UUID userId, String normalizedName);
}
