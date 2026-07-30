package com.example.whatsanki.repository;

import com.example.whatsanki.domain.PracticeSessionItemEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionItemRepository extends Repository<PracticeSessionItemEntity, UUID> {
    <S extends PracticeSessionItemEntity> List<S> saveAll(Iterable<S> items);
    Optional<PracticeSessionItemEntity> findBySessionIdAndUserIdAndPosition(
            UUID sessionId, UUID userId, int position
    );
    long countBySessionIdAndUserId(UUID sessionId, UUID userId);
}
