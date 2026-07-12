package com.example.whatsanki.repository;

import com.example.whatsanki.domain.BackgroundJobEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackgroundJobRepository extends Repository<BackgroundJobEntity, UUID> {
    BackgroundJobEntity save(BackgroundJobEntity job);
    Optional<BackgroundJobEntity> findByIdAndUserId(UUID id, UUID userId);
    List<BackgroundJobEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
