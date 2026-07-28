package com.example.whatsanki.repository;

import com.example.whatsanki.domain.SourceUploadEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface SourceUploadRepository extends Repository<SourceUploadEntity, UUID> {
    SourceUploadEntity save(SourceUploadEntity upload);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SourceUploadEntity> findForUpdateByIdAndUserId(UUID id, UUID userId);
}
