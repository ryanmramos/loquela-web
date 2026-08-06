package com.example.whatsanki.repository;

import com.example.whatsanki.domain.AccessEntryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessEntryRepository extends
        JpaRepository<AccessEntryEntity, UUID>,
        JpaSpecificationExecutor<AccessEntryEntity> {

    Optional<AccessEntryEntity> findByNormalizedEmail(String normalizedEmail);

    boolean existsByRoleAndStatus(AccessEntryEntity.Role role, AccessEntryEntity.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entry from AccessEntryEntity entry
            where entry.role = com.example.whatsanki.domain.AccessEntryEntity.Role.ADMIN
              and entry.status = com.example.whatsanki.domain.AccessEntryEntity.Status.ACTIVE
            order by entry.id
            """)
    List<AccessEntryEntity> lockActiveAdmins();
}

