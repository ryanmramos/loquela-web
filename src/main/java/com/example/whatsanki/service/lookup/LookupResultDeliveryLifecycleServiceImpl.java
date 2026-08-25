package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.OutboundMessageEntity;
import com.example.whatsanki.domain.OutboundMessageId;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.LookupDeliveryPartDto;
import com.example.whatsanki.dto.LookupDeliveryPreparationDto;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.OutboundMessageRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Builds an immutable outbound-message outbox and records provider delivery state. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LookupResultDeliveryLifecycleServiceImpl implements LookupResultDeliveryLifecycleService {
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final LookupSessionRepository lookupSessionRepository;
    private final OutboundMessageRepository outboundMessageRepository;
    private final UserIdentityService userIdentityService;
    private final LookupResultMessageFormatter messageFormatter;
    private final LookupFailureMessageFormatter failureMessageFormatter;

    /** Creates the outbox once, then returns only pending or previously accepted parts. */
    @Override
    @Transactional
    public LookupDeliveryPreparationDto prepare(
            UserId userId,
            LookupSessionId lookupSessionId,
            Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        LookupSessionEntity session = lookupSessionRepository
                .findByIdAndUserId(lookupSessionId.value(), userId.value())
                .orElseThrow(() -> OutboundDeliveryException.permanent("LOOKUP_SESSION_NOT_FOUND"));
        boolean successful = session.getStatus() == LookupSessionEntity.Status.READY;
        boolean failed = session.getStatus() == LookupSessionEntity.Status.FAILED;
        if (!successful && !failed) {
            throw OutboundDeliveryException.permanent("LOOKUP_RESULT_NOT_READY");
        }
        if (successful && !session.getExpiresAt().isAfter(now)) {
            throw OutboundDeliveryException.permanent("LOOKUP_RESULT_EXPIRED");
        }

        List<OutboundMessageEntity> records = outboundMessageRepository
                .findAllByUserIdAndLookupSessionIdOrderByPartNumber(
                        userId.value(), lookupSessionId.value()
        );
        if (records.isEmpty()) {
            // Both success and terminal failure use the same immutable outbox. Failure
            // bodies are derived only from a safe code and never expose provider details.
            List<String> bodies = successful
                    ? messageFormatter.format(session.getResultPayload())
                    : List.of(failureMessageFormatter.format(requiredErrorCode(session)));
            records = outboundMessageRepository.saveAll(
                    createRecords(userId, lookupSessionId, bodies, now)
            );
        }
        validateRecords(records);
        if (records.stream().anyMatch(record -> record.getStatus() == OutboundMessageEntity.Status.FAILED)) {
            throw OutboundDeliveryException.permanent("OUTBOUND_NOT_DELIVERABLE");
        }

        WhatsAppAddress recipient;
        try {
            recipient = userIdentityService.requireWhatsAppAddress(userId);
        } catch (RuntimeException exception) {
            throw new OutboundDeliveryException(
                    BackgroundJobFailureKind.PERMANENT,
                    "IDENTITY_DECRYPTION_FAILED",
                    exception
            );
        }

        List<LookupDeliveryPartDto> parts = new ArrayList<>(records.size());
        for (int index = 0; index < records.size(); index++) {
            OutboundMessageEntity record = records.get(index);
            parts.add(new LookupDeliveryPartDto(
                    new OutboundMessageId(record.getId()),
                    record.getPartNumber(),
                    record.getBody(),
                    record.getStatus() == OutboundMessageEntity.Status.PROVIDER_ACCEPTED
            ));
        }
        log.info(
                "Prepared lookup outcome delivery userId={} lookupSessionId={} lookupStatus={} "
                        + "partCount={} acceptedCount={}",
                userId.value(), lookupSessionId.value(), session.getStatus(), parts.size(),
                parts.stream().filter(LookupDeliveryPartDto::providerAccepted).count()
        );
        return new LookupDeliveryPreparationDto(recipient, parts);
    }

    @Override
    @Transactional
    public void markProviderAccepted(
            UserId userId,
            OutboundMessageId outboundMessageId,
            ProviderMessageId providerMessageId,
            Instant now
    ) {
        Objects.requireNonNull(providerMessageId, "providerMessageId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        OutboundMessageEntity record = ownedRecord(userId, outboundMessageId);
        if (record.getStatus() == OutboundMessageEntity.Status.PROVIDER_ACCEPTED) {
            if (!record.getProviderMessageSid().equals(providerMessageId.value())) {
                throw OutboundDeliveryException.permanent("OUTBOUND_PROVIDER_ID_MISMATCH");
            }
            return;
        }
        if (record.getStatus() != OutboundMessageEntity.Status.PENDING) {
            throw OutboundDeliveryException.permanent("OUTBOUND_NOT_DELIVERABLE");
        }
        record.setStatus(OutboundMessageEntity.Status.PROVIDER_ACCEPTED);
        record.setProviderMessageSid(providerMessageId.value());
        record.setProviderAcceptedAt(now);
        record.setErrorCode(null);
        record.setUpdatedAt(now);
        outboundMessageRepository.save(record);
        log.info(
                "Recorded provider-accepted outbound message userId={} outboundMessageId={} partNumber={}",
                userId.value(), outboundMessageId.value(), record.getPartNumber()
        );
    }

    @Override
    @Transactional
    public void markFailed(
            UserId userId,
            LookupSessionId lookupSessionId,
            String errorCode,
            Instant now
    ) {
        validateErrorCode(errorCode);
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        List<OutboundMessageEntity> records = outboundMessageRepository
                .findAllByUserIdAndLookupSessionIdOrderByPartNumber(
                        userId.value(), lookupSessionId.value()
                );
        records.stream()
                .filter(record -> record.getStatus() == OutboundMessageEntity.Status.PENDING)
                .forEach(record -> {
                    record.setStatus(OutboundMessageEntity.Status.FAILED);
                    record.setErrorCode(errorCode);
                    record.setUpdatedAt(now);
                });
        outboundMessageRepository.saveAll(records);
        log.warn(
                "Marked pending lookup delivery parts failed userId={} lookupSessionId={} errorCode={}",
                userId.value(), lookupSessionId.value(), errorCode
        );
    }

    private List<OutboundMessageEntity> createRecords(
            UserId userId,
            LookupSessionId lookupSessionId,
            List<String> bodies,
            Instant now
    ) {
        List<OutboundMessageEntity> records = new ArrayList<>(bodies.size());
        for (int index = 0; index < bodies.size(); index++) {
            OutboundMessageEntity record = new OutboundMessageEntity();
            record.setId(OutboundMessageId.random().value());
            record.setUserId(userId.value());
            record.setLookupSessionId(lookupSessionId.value());
            record.setPartNumber(index + 1);
            record.setBody(bodies.get(index));
            record.setStatus(OutboundMessageEntity.Status.PENDING);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            records.add(record);
        }
        return records;
    }

    private void validateRecords(List<OutboundMessageEntity> records) {
        for (int index = 0; index < records.size(); index++) {
            OutboundMessageEntity record = records.get(index);
            if (record.getPartNumber() != index + 1
                    || record.getBody() == null
                    || record.getBody().isBlank()) {
                throw OutboundDeliveryException.permanent("OUTBOUND_SNAPSHOT_MISMATCH");
            }
        }
    }

    private OutboundMessageEntity ownedRecord(UserId userId, OutboundMessageId outboundMessageId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(outboundMessageId, "outboundMessageId must not be null");
        return outboundMessageRepository.findByIdAndUserId(
                        outboundMessageId.value(), userId.value()
                )
                .orElseThrow(() -> OutboundDeliveryException.permanent("OUTBOUND_MESSAGE_NOT_FOUND"));
    }

    private void validateErrorCode(String errorCode) {
        if (errorCode == null || !SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be an uppercase safe diagnostic code");
        }
    }

    private String requiredErrorCode(LookupSessionEntity session) {
        String errorCode = session.getErrorCode();
        if (errorCode == null || !SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw OutboundDeliveryException.permanent("LOOKUP_FAILURE_INVALID");
        }
        return errorCode;
    }
}
