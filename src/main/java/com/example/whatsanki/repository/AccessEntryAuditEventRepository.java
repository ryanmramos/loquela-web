package com.example.whatsanki.repository;

import com.example.whatsanki.domain.AccessEntryAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessEntryAuditEventRepository extends JpaRepository<AccessEntryAuditEventEntity, UUID> {
}

