package com.example.whatsanki.service.export;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.DeckExportAcceptanceDto;
import com.example.whatsanki.dto.DeckExportPreparationDto;
import com.example.whatsanki.dto.ExportDeckJobPayloadDto;
import com.example.whatsanki.dto.ExportedDeckDto;
import com.example.whatsanki.dto.OutboundMessageDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.card.DefaultDeckService;
import com.example.whatsanki.service.exception.DeckExportException;
import com.example.whatsanki.service.exception.OutboundDeliveryException;
import com.example.whatsanki.service.job.BackgroundJobService;
import com.example.whatsanki.service.storage.ObjectStore;
import com.example.whatsanki.service.twilio.OutboundMessenger;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckExportServiceImpl implements DeckExportService {
    private static final int SNAPSHOT_SCHEMA_VERSION = 1;
    private static final String TEMPLATE_VERSION = AnkiCardFieldsDto.TEMPLATE_VERSION;

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final AppUserRepository appUserRepository;
    private final ExportRequestRepository exportRequestRepository;
    private final BackgroundJobService backgroundJobService;
    private final DeckExportLifecycleService lifecycleService;
    private final DeckExporter deckExporter;
    private final ObjectStore objectStore;
    private final ObjectKeyFactory objectKeyFactory;
    private final UserIdentityService userIdentityService;
    private final OutboundMessenger outboundMessenger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public DeckExportAcceptanceDto accept(UserId userId, String deckName, String normalizedDeckName) {
        Objects.requireNonNull(userId, "userId must not be null");
        String normalized = normalizedDeckName == null || normalizedDeckName.isBlank()
                ? DefaultDeckService.DEFAULT_DECK_NORMALIZED_NAME : normalizedDeckName;
        var deck = deckRepository.findByUserIdAndNormalizedName(userId.value(), normalized)
                .filter(candidate -> candidate.getStatus() == com.example.whatsanki.domain.DeckEntity.Status.ACTIVE)
                .orElseThrow(() -> DeckExportException.permanent("DECK_NOT_FOUND"));
        List<CardEntity> cards = cardRepository.findAllByUserIdAndDeckIdOrderByCreatedAt(
                userId.value(), deck.getId()
        );
        if (cards.isEmpty()) {
            throw DeckExportException.permanent("DECK_EMPTY");
        }
        AppUserEntity user = appUserRepository.findById(userId.value())
                .orElseThrow(() -> DeckExportException.permanent("USER_NOT_FOUND"));

        ExportRequestId exportRequestId = ExportRequestId.random();
        String exportedDeckName = user.getExportDeckPrefix() + deck.getName();
        Map<String, Object> snapshot = snapshot(
                exportRequestId,
                deck.getId().toString(),
                exportedDeckName,
                cards,
                user.isExportIncludeProvenance()
        );
        byte[] snapshotSha256 = sha256(canonicalJson(snapshot));

        ExportRequestEntity export = new ExportRequestEntity();
        export.setId(exportRequestId.value());
        export.setUserId(userId.value());
        export.setDeckId(deck.getId());
        export.setState(ExportRequestEntity.State.QUEUED);
        export.setSnapshotPayload(snapshot);
        export.setSnapshotSha256(snapshotSha256);
        export.setTemplateVersion(TEMPLATE_VERSION);
        exportRequestRepository.save(export);
        JobId jobId = backgroundJobService.enqueue(
                userId,
                BackgroundJobEntity.Type.EXPORT_DECK,
                ExportDeckJobPayloadDto.create(exportRequestId).toMap()
        );
        log.info("Accepted deck export userId={} deckId={} exportRequestId={} jobId={} cardCount={}",
                userId.value(), deck.getId(), exportRequestId.value(), jobId.value(), cards.size());
        return new DeckExportAcceptanceDto(exportRequestId, jobId, deck.getName(), cards.size());
    }

    @Override
    public void execute(UserId userId, ExportRequestId exportRequestId) {
        DeckExportPreparationDto preparation = lifecycleService.prepare(userId, exportRequestId);
        String artifactKey = preparation.artifactKey();
        Instant expiresAt = preparation.expiresAt();
        if (!preparation.artifactReady()) {
            verifySnapshotChecksum(preparation);
            ExportedDeckDto artifact = deckExporter.export(preparation.snapshot());
            try {
                artifactKey = objectKeyFactory.exportedDeck(userId, exportRequestId);
                objectStore.put(
                        artifactKey,
                        artifact.path(),
                        new StoredObjectMetadataDto(
                                artifact.byteSize(), artifact.sha256(), "application/vnd.anki"
                        )
                );
                expiresAt = lifecycleService.markReady(
                        userId, exportRequestId, artifactKey, artifact.sha256(), Instant.now()
                );
            } catch (DeckExportException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw DeckExportException.transientFailure("EXPORT_UPLOAD_FAILED", exception);
            } finally {
                cleanupArtifact(artifact);
            }
        }
        // Browser-only accounts retrieve this same artifact through the authorized
        // download endpoint and must not fail merely because they have no WhatsApp identity.
        if (userIdentityService.hasIdentity(userId, UserIdentityEntity.Provider.WHATSAPP)) {
            deliverReadyLink(userId, exportRequestId, preparation.deckName(), artifactKey, expiresAt);
        }
    }

    @Override
    public void markFailed(UserId userId, ExportRequestId exportRequestId, String errorCode) {
        lifecycleService.markFailed(userId, exportRequestId, errorCode);
    }

    private Map<String, Object> snapshot(
            ExportRequestId exportRequestId,
            String deckId,
            String deckName,
            List<CardEntity> cards,
            boolean includeProvenance
    ) {
        List<Map<String, Object>> cardSnapshots = new ArrayList<>();
        for (CardEntity card : cards) {
            if (!TEMPLATE_VERSION.equals(card.getAnkiTemplateVersion())) {
                throw DeckExportException.permanent("CARD_TEMPLATE_UNSUPPORTED");
            }
            Object ankiValue = card.getPayload().get("anki");
            if (!(ankiValue instanceof Map<?, ?> anki)) {
                throw DeckExportException.permanent("CARD_SNAPSHOT_INVALID");
            }
            cardSnapshots.add(cardSnapshot(card, anki, includeProvenance));
        }
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("exportId", exportRequestId.value().toString());
        snapshot.put("deck", orderedMap("stableId", deckId, "name", deckName));
        snapshot.put("templateVersion", TEMPLATE_VERSION);
        snapshot.put("cards", List.copyOf(cardSnapshots));
        return Collections.unmodifiableMap(snapshot);
    }

    private Map<String, Object> cardSnapshot(
            CardEntity card,
            Map<?, ?> anki,
            boolean includeProvenance
    ) {
        if (!(anki.get("schemaVersion") instanceof Number version) || version.intValue() != 1
                || !AnkiCardFieldsDto.NOTE_TYPE_NAME.equals(anki.get("noteTypeName"))
                || !TEMPLATE_VERSION.equals(anki.get("templateVersion"))
                || !(anki.get("fields") instanceof Map<?, ?> storedFields)) {
            throw DeckExportException.permanent("CARD_SNAPSHOT_INVALID");
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (String field : AnkiCardFieldsDto.FIELD_ORDER) {
            Object value = storedFields.get(field);
            if (!(value instanceof String text)) {
                throw DeckExportException.permanent("CARD_SNAPSHOT_INVALID");
            }
            fields.put(
                    field,
                    !includeProvenance && ("SourceName".equals(field) || "SourceLocator".equals(field))
                            ? ""
                            : text
            );
        }
        if (storedFields.size() != fields.size()) {
            throw DeckExportException.permanent("CARD_SNAPSHOT_INVALID");
        }
        LinkedHashMap<String, Object> ankiSnapshot = new LinkedHashMap<>();
        ankiSnapshot.put("schemaVersion", 1);
        ankiSnapshot.put("noteTypeName", AnkiCardFieldsDto.NOTE_TYPE_NAME);
        ankiSnapshot.put("templateVersion", TEMPLATE_VERSION);
        ankiSnapshot.put("fields", Collections.unmodifiableMap(fields));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("stableId", card.getId().toString());
        result.put("noteType", card.getNoteType().name());
        result.put("anki", Collections.unmodifiableMap(ankiSnapshot));
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Object> orderedMap(String firstKey, Object first, String secondKey, Object second) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(firstKey, first);
        values.put(secondKey, second);
        return Collections.unmodifiableMap(values);
    }

    private void verifySnapshotChecksum(DeckExportPreparationDto preparation) {
        byte[] actual = sha256(canonicalJson(preparation.snapshot()));
        if (!MessageDigest.isEqual(actual, preparation.snapshotSha256())) {
            throw DeckExportException.permanent("EXPORT_SNAPSHOT_CHECKSUM_MISMATCH");
        }
    }

    private void deliverReadyLink(
            UserId userId,
            ExportRequestId exportRequestId,
            String deckName,
            String artifactKey,
            Instant expiresAt
    ) {
        if (artifactKey == null || expiresAt == null) {
            throw DeckExportException.permanent("EXPORT_ARTIFACT_INVALID");
        }
        Duration lifetime = Duration.between(Instant.now(), expiresAt);
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw DeckExportException.permanent("EXPORT_ARTIFACT_EXPIRED");
        }
        try {
            URI url = objectStore.createPresignedGetUrl(artifactKey, lifetime);
            outboundMessenger.send(
                    userIdentityService.requireWhatsAppAddress(userId),
                    new OutboundMessageDto("Your *" + deckName + "* deck is ready: " + url
                            + "\nThis private link expires at " + expiresAt + ".")
            );
            log.info("Delivered deck export link userId={} exportRequestId={} expiresAt={}",
                    userId.value(), exportRequestId.value(), expiresAt);
        } catch (DeckExportException exception) {
            throw exception;
        } catch (OutboundDeliveryException exception) {
            throw new DeckExportException(exception.failureKind(), exception.errorCode(), exception);
        } catch (RuntimeException exception) {
            throw DeckExportException.transientFailure("EXPORT_DELIVERY_FAILED", exception);
        }
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** JSONB does not preserve object-key order, so the persisted digest uses a
     * canonical key-sorted serialization on both acceptance and execution. */
    private byte[] canonicalJson(Map<String, Object> snapshot) {
        return objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsBytes(snapshot);
    }

    private void cleanupArtifact(ExportedDeckDto artifact) {
        try {
            Files.deleteIfExists(artifact.path());
            Files.deleteIfExists(artifact.path().getParent());
        } catch (IOException exception) {
            log.warn("Could not remove temporary deck artifact fileName={}", artifact.path().getFileName());
        }
    }
}
