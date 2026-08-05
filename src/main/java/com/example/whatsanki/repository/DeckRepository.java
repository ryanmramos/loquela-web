package com.example.whatsanki.repository;

import com.example.whatsanki.domain.DeckEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Set;

public interface DeckRepository extends Repository<DeckEntity, UUID> {
    DeckEntity save(DeckEntity deck);
    DeckEntity saveAndFlush(DeckEntity deck);
    Optional<DeckEntity> findByIdAndUserId(UUID id, UUID userId);
    List<DeckEntity> findAllByUserIdAndIdIn(UUID userId, Set<UUID> ids);
    Optional<DeckEntity> findByUserIdAndNormalizedName(UUID userId, String normalizedName);
    List<DeckEntity> findAllByUserIdAndStatusOrderByName(UUID userId, DeckEntity.Status status);
    Page<DeckEntity> findAllByUserIdAndStatusAndNormalizedNameContaining(
            UUID userId, DeckEntity.Status status, String normalizedName, Pageable pageable
    );
    void delete(DeckEntity deck);

    @Modifying
    @Query(value = """
            insert into deck (id, user_id, name, normalized_name, status, created_at, updated_at)
            values (:id, :userId, :name, :normalizedName, 'ACTIVE', :now, :now)
            on conflict (user_id, normalized_name) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("name") String name,
            @Param("normalizedName") String normalizedName,
            @Param("now") Instant now
    );
}
