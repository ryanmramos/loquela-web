package com.example.whatsanki.repository;

import com.example.whatsanki.domain.OutboundMessageEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboundMessageRepository extends Repository<OutboundMessageEntity, UUID> {
    OutboundMessageEntity save(OutboundMessageEntity outboundMessage);
    List<OutboundMessageEntity> saveAll(Iterable<OutboundMessageEntity> outboundMessages);
    List<OutboundMessageEntity> findAllByUserIdAndLookupSessionIdOrderByPartNumber(
            UUID userId,
            UUID lookupSessionId
    );
    Optional<OutboundMessageEntity> findByIdAndUserId(UUID id, UUID userId);
}
