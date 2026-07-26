package com.example.whatsanki.repository;

import com.example.whatsanki.domain.UserIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {

    Optional<UserIdentityEntity> findByProviderAndProviderSubjectHash(
            UserIdentityEntity.Provider provider,
            byte[] providerSubjectHash
    );

    Optional<UserIdentityEntity> findByUserIdAndProvider(
            UUID userId,
            UserIdentityEntity.Provider provider
    );

    boolean existsByUserIdAndProvider(UUID userId, UserIdentityEntity.Provider provider);

    @Modifying
    @Query(value = """
            insert into user_identity (
                id, user_id, provider, provider_subject_hash, provider_subject_ciphertext,
                display_email, display_name, verified_at, created_at, updated_at
            ) values (
                :id, :userId, :provider, :subjectHash, :subjectCiphertext,
                :displayEmail, :displayName, :now, :now, :now
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("provider") String provider,
            @Param("subjectHash") byte[] subjectHash,
            @Param("subjectCiphertext") byte[] subjectCiphertext,
            @Param("displayEmail") String displayEmail,
            @Param("displayName") String displayName,
            @Param("now") Instant now
    );
}
