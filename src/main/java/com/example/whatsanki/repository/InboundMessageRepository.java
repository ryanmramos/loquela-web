package com.example.whatsanki.repository;

import com.example.whatsanki.domain.InboundMessageEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface InboundMessageRepository extends Repository<InboundMessageEntity, UUID> {
    InboundMessageEntity save(InboundMessageEntity message);
    Optional<InboundMessageEntity> findByTwilioMessageSid(String twilioMessageSid);
    Optional<InboundMessageEntity> findByIdAndUserId(UUID id, UUID userId);
}
