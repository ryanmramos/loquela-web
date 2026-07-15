package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceDocumentEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceDocumentRepository extends Repository<SourceDocumentEntity, UUID> {
    SourceDocumentEntity save(SourceDocumentEntity source);
    Optional<SourceDocumentEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<SourceDocumentEntity> findByUserIdAndInboundMessageId(UUID userId, UUID inboundMessageId);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    long deleteByIdAndUserId(UUID id, UUID userId);
}
