package com.example.whatsanki.repository;

import com.example.whatsanki.domain.InboundMessageEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InboundMessageRepository extends Repository<InboundMessageEntity, UUID> {
    InboundMessageEntity save(InboundMessageEntity message);
    Optional<InboundMessageEntity> findByTwilioMessageSid(String twilioMessageSid);
    Optional<InboundMessageEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query(value = """
            insert into inbound_message (
                id, twilio_message_sid, user_id, command_type, received_at, purge_after
            ) values (
                :id, :messageSid, :userId, :commandType, :receivedAt, :purgeAfter
            )
            on conflict (twilio_message_sid) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("messageSid") String messageSid,
            @Param("userId") UUID userId,
            @Param("commandType") String commandType,
            @Param("receivedAt") Instant receivedAt,
            @Param("purgeAfter") Instant purgeAfter
    );
}
