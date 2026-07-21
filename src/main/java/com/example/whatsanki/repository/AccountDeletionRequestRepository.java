package com.example.whatsanki.repository;

import com.example.whatsanki.domain.AccountDeletionRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequestEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AccountDeletionRequestEntity request where request.userId = :userId")
    Optional<AccountDeletionRequestEntity> findByUserIdForUpdate(@Param("userId") UUID userId);
}
