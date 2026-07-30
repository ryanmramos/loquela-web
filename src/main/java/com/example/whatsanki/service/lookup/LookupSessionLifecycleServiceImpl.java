package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.DeliverLookupResultJobPayloadDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupExecutionPreparationDto;
import com.example.whatsanki.dto.LookupJobPayloadDto;
import com.example.whatsanki.dto.LookupResultPayloadDto;
import com.example.whatsanki.dto.LookupRequestFiltersDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.service.exception.LookupExecutionException;
import com.example.whatsanki.service.job.BackgroundJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupSessionLifecycleServiceImpl implements LookupSessionLifecycleService {

    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final LookupSessionRepository lookupSessionRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final BackgroundJobService backgroundJobService;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public LookupAcceptanceDto accept(
            UserId userId,
            InboundMessageId inboundMessageId,
            String requestedTerm,
            String normalizedTerm,
            LanguagePairDto languagePair,
            Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(inboundMessageId, "inboundMessageId must not be null");
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        Objects.requireNonNull(now, "now must not be null");

        var existing = lookupSessionRepository.findByUserIdAndInboundMessageId(
                userId.value(), inboundMessageId.value()
        );
        if (existing.isPresent()) {
            LookupSessionEntity session = existing.orElseThrow();
            log.info(
                    "Reused lookup acceptance userId={} lookupSessionId={} status={}",
                    userId.value(), session.getId(), session.getStatus()
            );
            return acceptance(session);
        }

        InboundMessageEntity inboundMessage = inboundMessageRepository
                .findByIdAndUserId(inboundMessageId.value(), userId.value())
                .orElseThrow(() -> new IllegalStateException("Inbound message was not found for LOOKUP command"));
        if (inboundMessage.getCommandType() != InboundMessageEntity.CommandType.LOOKUP) {
            throw new IllegalStateException("Inbound message is not a LOOKUP command");
        }

        LookupSessionId lookupSessionId = LookupSessionId.random();
        LookupSessionEntity session = new LookupSessionEntity();
        session.setId(lookupSessionId.value());
        session.setUserId(userId.value());
        session.setInboundMessageId(inboundMessageId.value());
        session.setRequestedTerm(required(requestedTerm, "requestedTerm"));
        session.setNormalizedTerm(required(normalizedTerm, "normalizedTerm"));
        session.setSourceLanguage(languagePair.sourceLanguage());
        session.setDefinitionLanguage(languagePair.definitionLanguage());
        session.setResultPayload(Map.of("schemaVersion", LookupResultPayloadDto.CURRENT_SCHEMA_VERSION));
        session.setRequestFilters(LookupRequestFiltersDto.none().toMap());
        session.setStatus(LookupSessionEntity.Status.PENDING);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(configuration.getRetention().getLookupSession()));
        lookupSessionRepository.save(session);

        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.LOOKUP,
                LookupJobPayloadDto.create(lookupSessionId).toMap()
        );
        log.info(
                "Accepted lookup userId={} inboundMessageId={} lookupSessionId={} jobId={} "
                        + "sourceLanguage={} definitionLanguage={} termCharacters={}",
                userId.value(),
                inboundMessageId.value(),
                lookupSessionId.value(),
                jobId.value(),
                languagePair.sourceLanguage(),
                languagePair.definitionLanguage(),
                requestedTerm.codePointCount(0, requestedTerm.length())
        );
        return acceptance(session);
    }

    @Override
    @Transactional
    public LookupAcceptanceDto acceptBrowser(
            UserId userId, String requestedTerm, String normalizedTerm, LanguagePairDto languagePair,
            LookupRequestFiltersDto filters, Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(languagePair, "languagePair must not be null");
        Objects.requireNonNull(now, "now must not be null");
        LookupSessionId lookupSessionId = LookupSessionId.random();
        LookupSessionEntity session = new LookupSessionEntity();
        session.setId(lookupSessionId.value());
        session.setUserId(userId.value());
        session.setRequestedTerm(required(requestedTerm, "requestedTerm"));
        session.setNormalizedTerm(required(normalizedTerm, "normalizedTerm"));
        session.setSourceLanguage(languagePair.sourceLanguage());
        session.setDefinitionLanguage(languagePair.definitionLanguage());
        session.setResultPayload(Map.of("schemaVersion", LookupResultPayloadDto.CURRENT_SCHEMA_VERSION));
        session.setRequestFilters((filters == null ? LookupRequestFiltersDto.none() : filters).toMap());
        session.setStatus(LookupSessionEntity.Status.PENDING);
        session.setCreatedAt(now); session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(configuration.getRetention().getLookupSession()));
        lookupSessionRepository.save(session);
        backgroundJobService.enqueue(userId, BackgroundJobEntity.Type.LOOKUP, LookupJobPayloadDto.create(lookupSessionId).toMap());
        return acceptance(session);
    }

    @Override
    @Transactional(readOnly = true)
    public LookupExecutionPreparationDto prepare(
            UserId userId,
            LookupSessionId lookupSessionId,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        LookupSessionEntity session = ownedSession(userId, lookupSessionId);
        if (session.getStatus() == LookupSessionEntity.Status.FAILED) {
            throw LookupExecutionException.permanent("LOOKUP_NOT_EXECUTABLE");
        }
        boolean alreadyCompleted = session.getStatus() == LookupSessionEntity.Status.READY
                && session.getExpiresAt().isAfter(now);
        boolean expired = session.getStatus() == LookupSessionEntity.Status.EXPIRED
                || !session.getExpiresAt().isAfter(now);
        return new LookupExecutionPreparationDto(
                lookupSessionId,
                session.getRequestedTerm(),
                session.getNormalizedTerm(),
                new LanguagePairDto(session.getSourceLanguage(), session.getDefinitionLanguage()),
                LookupRequestFiltersDto.fromMap(session.getRequestFilters()),
                session.getExpiresAt(),
                alreadyCompleted,
                expired
        );
    }

    @Override
    @Transactional
    public boolean complete(
            UserId userId,
            LookupSessionId lookupSessionId,
            DictionaryEntryDto entry,
            List<ContextExampleDto> contexts,
            Instant now
    ) {
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(contexts, "contexts must not be null");
        Objects.requireNonNull(now, "now must not be null");
        LookupSessionEntity session = ownedSession(userId, lookupSessionId);
        if (session.getStatus() == LookupSessionEntity.Status.READY) {
            if (session.getExpiresAt().isAfter(now)) {
                return true;
            }
            transitionToExpired(session, now);
            return false;
        }
        if (session.getStatus() != LookupSessionEntity.Status.PENDING) {
            throw LookupExecutionException.permanent("LOOKUP_NOT_EXECUTABLE");
        }
        if (!session.getExpiresAt().isAfter(now)) {
            transitionToExpired(session, now);
            return false;
        }

        LanguagePairDto snapshottedPair = new LanguagePairDto(
                session.getSourceLanguage(), session.getDefinitionLanguage()
        );
        if (!snapshottedPair.equals(entry.languagePair())) {
            throw LookupExecutionException.permanent("PROVIDER_LANGUAGE_MISMATCH");
        }
        if (contexts.stream().anyMatch(context -> !context.sourceLanguage().value()
                .equals(snapshottedPair.sourceLanguage()))) {
            throw LookupExecutionException.permanent("CONTEXT_LANGUAGE_MISMATCH");
        }

        // The final payload is an immutable numbered snapshot. Source-specific location
        // data is copied verbatim, so PDF page numbers and future video timestamps survive.
        session.setResultPayload(LookupResultPayloadDto.create(entry, contexts).toMap());
        session.setStatus(LookupSessionEntity.Status.READY);
        session.setErrorCode(null);
        session.setUpdatedAt(now);
        lookupSessionRepository.save(session);
        // Browser lookups are rendered by the website polling endpoint. They must not attempt
        // WhatsApp delivery: a website-only account may intentionally have no decryptable number.
        if (session.getInboundMessageId() != null) {
            backgroundJobService.enqueue(
                    userId,
                    BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                    DeliverLookupResultJobPayloadDto.create(lookupSessionId).toMap()
            );
        }
        log.info(
                "Completed {} lookup session userId={} lookupSessionId={} providerId={} contextCount={}",
                session.getInboundMessageId() == null ? "browser" : "WhatsApp", userId.value(),
                lookupSessionId.value(), entry.provenance().providerId(), contexts.size()
        );
        return true;
    }

    @Override
    @Transactional
    public void markExpired(UserId userId, LookupSessionId lookupSessionId, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        LookupSessionEntity session = ownedSession(userId, lookupSessionId);
        if (!session.getExpiresAt().isAfter(now)
                && (session.getStatus() == LookupSessionEntity.Status.PENDING
                    || session.getStatus() == LookupSessionEntity.Status.READY)) {
            transitionToExpired(session, now);
        }
    }

    @Override
    @Transactional
    public void markFailed(
            UserId userId,
            LookupSessionId lookupSessionId,
            String errorCode,
            Instant now
    ) {
        if (errorCode == null || !SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("errorCode must be an uppercase safe diagnostic code");
        }
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        var sessionResult = lookupSessionRepository.findByIdAndUserId(
                lookupSessionId.value(), userId.value()
        );
        if (sessionResult.isEmpty()) {
            log.warn(
                    "Could not mark missing lookup session failed userId={} lookupSessionId={} errorCode={}",
                    userId.value(), lookupSessionId.value(), errorCode
            );
            return;
        }
        LookupSessionEntity session = sessionResult.orElseThrow();
        if (session.getStatus() != LookupSessionEntity.Status.PENDING) {
            return;
        }
        session.setStatus(LookupSessionEntity.Status.FAILED);
        session.setErrorCode(errorCode);
        session.setUpdatedAt(now);
        lookupSessionRepository.save(session);
        if (session.getInboundMessageId() != null) {
            JobId deliveryJobId = backgroundJobService.enqueue(
                    userId,
                    BackgroundJobEntity.Type.DELIVER_LOOKUP_RESULT,
                    DeliverLookupResultJobPayloadDto.create(lookupSessionId).toMap()
            );
            log.warn(
                    "Marked WhatsApp lookup session failed and queued notification userId={} lookupSessionId={} "
                            + "deliveryJobId={} errorCode={}",
                    userId.value(), lookupSessionId.value(), deliveryJobId.value(), errorCode
            );
        }
    }

    private LookupSessionEntity ownedSession(UserId userId, LookupSessionId lookupSessionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        return lookupSessionRepository.findByIdAndUserId(lookupSessionId.value(), userId.value())
                .orElseThrow(() -> LookupExecutionException.permanent("LOOKUP_SESSION_NOT_FOUND"));
    }

    private LookupAcceptanceDto acceptance(LookupSessionEntity session) {
        return new LookupAcceptanceDto(
                new LookupSessionId(session.getId()),
                new LanguagePairDto(session.getSourceLanguage(), session.getDefinitionLanguage()),
                session.getExpiresAt()
        );
    }

    private void transitionToExpired(LookupSessionEntity session, Instant now) {
        session.setStatus(LookupSessionEntity.Status.EXPIRED);
        session.setErrorCode(null);
        session.setUpdatedAt(now);
        lookupSessionRepository.save(session);
        log.info("Expired lookup session userId={} lookupSessionId={}", session.getUserId(), session.getId());
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
