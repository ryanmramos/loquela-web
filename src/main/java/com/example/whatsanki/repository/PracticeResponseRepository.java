package com.example.whatsanki.repository;

import com.example.whatsanki.domain.PracticeResponseEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeResponseRepository extends Repository<PracticeResponseEntity, UUID> {
    PracticeResponseEntity save(PracticeResponseEntity response);
    Optional<PracticeResponseEntity> findBySessionIdAndRequestId(UUID sessionId, UUID requestId);
    long countBySessionId(UUID sessionId);
}
