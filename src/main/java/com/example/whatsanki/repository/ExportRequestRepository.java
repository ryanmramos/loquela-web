package com.example.whatsanki.repository;

import com.example.whatsanki.domain.ExportRequestEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportRequestRepository extends Repository<ExportRequestEntity, UUID> {
    ExportRequestEntity save(ExportRequestEntity exportRequest);
    Optional<ExportRequestEntity> findByIdAndUserId(UUID id, UUID userId);
    List<ExportRequestEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
