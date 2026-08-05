package com.example.whatsanki.repository;

import com.example.whatsanki.domain.OutboundMessageEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface OutboundMessageRepository extends Repository<OutboundMessageEntity, UUID> {
    OutboundMessageEntity save(OutboundMessageEntity outboundMessage);
    List<OutboundMessageEntity> saveAll(Iterable<OutboundMessageEntity> outboundMessages);
    List<OutboundMessageEntity> findAllByUserIdAndLookupSessionIdOrderByPartNumber(
            UUID userId,
            UUID lookupSessionId
    );
    List<OutboundMessageEntity> findAllByUserIdAndLookupSessionIdIn(UUID userId, Set<UUID> lookupSessionIds);
    Optional<OutboundMessageEntity> findByIdAndUserId(UUID id, UUID userId);
}
