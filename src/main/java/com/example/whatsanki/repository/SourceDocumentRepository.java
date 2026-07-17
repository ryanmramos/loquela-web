package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceDocumentEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SourceDocumentRepository extends Repository<SourceDocumentEntity, UUID> {
    SourceDocumentEntity save(SourceDocumentEntity source);
    Optional<SourceDocumentEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<SourceDocumentEntity> findByUserIdAndInboundMessageId(UUID userId, UUID inboundMessageId);
    List<SourceDocumentEntity> findAllByUserIdAndIdIn(UUID userId, Set<UUID> ids);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<SourceDocumentEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    void delete(SourceDocumentEntity source);
    long deleteByIdAndUserId(UUID id, UUID userId);
}
