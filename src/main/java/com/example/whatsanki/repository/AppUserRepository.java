package com.example.whatsanki.repository;

import com.example.whatsanki.domain.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {
    Optional<AppUserEntity> findByWhatsappAddressHash(byte[] whatsappAddressHash);

    Optional<AppUserEntity> findByIdAndStatus(UUID id, AppUserEntity.Status status);

    @Modifying
    @Query(value = """
            insert into app_user (
                id, whatsapp_address_hash, whatsapp_address_ciphertext,
                source_language, definition_language, status, created_at, updated_at
            ) values (
                :id, :addressHash, :addressCiphertext,
                :sourceLanguage, :definitionLanguage, 'ACTIVE', :now, :now
            )
            on conflict (whatsapp_address_hash) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("addressHash") byte[] addressHash,
            @Param("addressCiphertext") byte[] addressCiphertext,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("definitionLanguage") String definitionLanguage,
            @Param("now") Instant now
    );
}
