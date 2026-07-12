package com.example.whatsanki.repository;

import com.example.whatsanki.domain.LookupSessionEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface LookupSessionRepository extends Repository<LookupSessionEntity, UUID> {
    LookupSessionEntity save(LookupSessionEntity session);
    Optional<LookupSessionEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<LookupSessionEntity> findByIdAndUserId(UUID id, UUID userId);
}
