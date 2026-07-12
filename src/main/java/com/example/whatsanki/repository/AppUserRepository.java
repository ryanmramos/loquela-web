package com.example.whatsanki.repository;

import com.example.whatsanki.domain.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {
    Optional<AppUserEntity> findByWhatsappAddressHash(byte[] whatsappAddressHash);
}
