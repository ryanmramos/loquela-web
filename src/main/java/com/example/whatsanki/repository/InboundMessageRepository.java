package com.example.whatsanki.repository;

import com.example.whatsanki.domain.InboundMessageEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface InboundMessageRepository extends Repository<InboundMessageEntity, UUID> {
    InboundMessageEntity save(InboundMessageEntity message);
    Optional<InboundMessageEntity> findByTwilioMessageSid(String twilioMessageSid);
    Optional<InboundMessageEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            select *
            from inbound_message
            where twilio_media_sid is not null
              and media_url_ciphertext is null
              and not exists (
                  select 1
                  from background_job job
                  where job.user_id = inbound_message.user_id
                    and job.type = 'DELETE_TWILIO_MEDIA'
                    and job.state in ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                    and job.payload ->> 'inboundMessageId' = inbound_message.id::text
              )
            order by received_at
            """, nativeQuery = true)
    List<InboundMessageEntity> findPendingTwilioMediaCleanup(Pageable pageable);

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
