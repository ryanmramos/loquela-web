package com.example.whatsanki.repository;

import com.example.whatsanki.domain.DeckEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends Repository<DeckEntity, UUID> {
    DeckEntity save(DeckEntity deck);
    Optional<DeckEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<DeckEntity> findByUserIdAndNormalizedName(UUID userId, String normalizedName);

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
