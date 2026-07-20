package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.CardEntity;
import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.ExportRequestId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.AnkiCardFieldsDto;
import com.example.whatsanki.dto.DeckExportPreparationDto;
import com.example.whatsanki.dto.ExportedDeckDto;
import com.example.whatsanki.dto.StoredObjectMetadataDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.service.BackgroundJobService;
import com.example.whatsanki.service.DeckExportLifecycleService;
import com.example.whatsanki.service.DeckExporter;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.ObjectStore;
import com.example.whatsanki.service.OutboundMessenger;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckExportServiceImplTest {
    @Mock private DeckRepository deckRepository;
    @Mock private CardRepository cardRepository;
    @Mock private ExportRequestRepository exportRequestRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private BackgroundJobService backgroundJobService;
    @Mock private DeckExportLifecycleService lifecycleService;
    @Mock private DeckExporter deckExporter;
    @Mock private ObjectStore objectStore;
    @Mock private IdentityProtectionService identityProtectionService;
    @Mock private OutboundMessenger outboundMessenger;
    @TempDir Path temporaryDirectory;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private DeckExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeckExportServiceImpl(
                deckRepository, cardRepository, exportRequestRepository, appUserRepository,
                backgroundJobService, lifecycleService, deckExporter, objectStore,
                new ObjectKeyFactory(), identityProtectionService, outboundMessenger, objectMapper
        );
    }

    @Test
    void transactionallySnapshotsStoredAnkiFieldsAndQueuesOnlyExportId() {
        UserId userId = UserId.random();
        DeckEntity deck = deck(userId, "Spanish Reading", "spanish reading");
        CardEntity card = card(userId, deck.getId());
        JobId jobId = JobId.random();
        when(deckRepository.findByUserIdAndNormalizedName(userId.value(), "spanish reading"))
                .thenReturn(Optional.of(deck));
        when(cardRepository.findAllByUserIdAndDeckIdOrderByCreatedAt(userId.value(), deck.getId()))
                .thenReturn(java.util.List.of(card));
        when(backgroundJobService.enqueue(eq(userId), eq(BackgroundJobEntity.Type.EXPORT_DECK), any()))
                .thenReturn(jobId);

        var accepted = service.accept(userId, "Spanish Reading", "spanish reading");

        ArgumentCaptor<ExportRequestEntity> export = ArgumentCaptor.forClass(ExportRequestEntity.class);
        verify(exportRequestRepository).save(export.capture());
        assertThat(export.getValue().getState()).isEqualTo(ExportRequestEntity.State.QUEUED);
        assertThat(export.getValue().getSnapshotPayload()).containsEntry("schemaVersion", 1);
        assertThat(export.getValue().getSnapshotSha256())
                .containsExactly(sha256(canonicalJson(export.getValue().getSnapshotPayload())));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstCard = (Map<String, Object>) ((java.util.List<?>)
                export.getValue().getSnapshotPayload().get("cards")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> anki = (Map<String, Object>) firstCard.get("anki");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) anki.get("fields");
        assertThat(fields.keySet()).containsExactlyElementsOf(AnkiCardFieldsDto.FIELD_ORDER);
        assertThat(fields.get("ContextBefore")).isEqualTo("Quiero ");
        verify(backgroundJobService).enqueue(
                eq(userId), eq(BackgroundJobEntity.Type.EXPORT_DECK),
                eq(Map.of("schemaVersion", 1, "exportRequestId", accepted.exportRequestId().value().toString()))
        );
    }

    @Test
    void publishesVerifiedArtifactThenSendsExpiringPrivateLink() throws Exception {
        UserId userId = UserId.random();
        ExportRequestId exportId = ExportRequestId.random();
        Map<String, Object> snapshot = Map.of("schemaVersion", 1);
        byte[] snapshotHash = sha256(canonicalJson(snapshot));
        Path workerDirectory = Files.createDirectory(temporaryDirectory.resolve("worker"));
        Path artifactPath = workerDirectory.resolve("deck.apkg");
        Files.writeString(artifactPath, "package");
        byte[] artifactHash = sha256(Files.readAllBytes(artifactPath));
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String objectKey = "users/" + userId.value() + "/exports/" + exportId.value() + "/deck.apkg";
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        user.setWhatsappAddressCiphertext(new byte[]{1});
        WhatsAppAddress address = new WhatsAppAddress("whatsapp:+15551234567");
        when(lifecycleService.prepare(userId, exportId)).thenReturn(new DeckExportPreparationDto(
                exportId, "Default", snapshot, snapshotHash, false, null, null
        ));
        when(deckExporter.export(snapshot)).thenReturn(new ExportedDeckDto(
                artifactPath, Files.size(artifactPath), artifactHash
        ));
        when(lifecycleService.markReady(eq(userId), eq(exportId), eq(objectKey), any(), any()))
                .thenReturn(expiresAt);
        when(objectStore.createPresignedGetUrl(eq(objectKey), any()))
                .thenReturn(URI.create("https://store.example/private-token"));
        when(appUserRepository.findByIdAndStatus(userId.value(), AppUserEntity.Status.ACTIVE))
                .thenReturn(Optional.of(user));
        when(identityProtectionService.decrypt(user.getWhatsappAddressCiphertext())).thenReturn(address);
        when(outboundMessenger.send(eq(address), any())).thenReturn(new ProviderMessageId("SM123"));

        service.execute(userId, exportId);

        ArgumentCaptor<StoredObjectMetadataDto> metadata = ArgumentCaptor.forClass(StoredObjectMetadataDto.class);
        verify(objectStore).put(eq(objectKey), eq(artifactPath), metadata.capture());
        assertThat(metadata.getValue().mediaType()).isEqualTo("application/vnd.anki");
        assertThat(metadata.getValue().sha256()).containsExactly(artifactHash);
        verify(outboundMessenger).send(eq(address), any());
        assertThat(artifactPath).doesNotExist();
    }

    private DeckEntity deck(UserId userId, String name, String normalizedName) {
        DeckEntity deck = new DeckEntity();
        deck.setId(UUID.randomUUID());
        deck.setUserId(userId.value());
        deck.setName(name);
        deck.setNormalizedName(normalizedName);
        deck.setStatus(DeckEntity.Status.ACTIVE);
        return deck;
    }

    private CardEntity card(UserId userId, UUID deckId) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        AnkiCardFieldsDto.FIELD_ORDER.forEach(name -> fields.put(name, ""));
        fields.put("Term", "vivir");
        fields.put("ContextBefore", "Quiero ");
        fields.put("ContextTarget", "vivir");
        fields.put("ContextAfter", " aquí.");
        fields.put("Definition", "to live");
        fields.put("TextRecognition", "1");
        CardEntity card = new CardEntity();
        card.setId(UUID.randomUUID());
        card.setUserId(userId.value());
        card.setDeckId(deckId);
        card.setNoteType(CardEntity.NoteType.BASIC_CONTEXT);
        card.setAnkiTemplateVersion(AnkiCardFieldsDto.TEMPLATE_VERSION);
        card.setPayload(Map.of("anki", Map.of(
                "schemaVersion", 1,
                "noteTypeName", AnkiCardFieldsDto.NOTE_TYPE_NAME,
                "templateVersion", AnkiCardFieldsDto.TEMPLATE_VERSION,
                "fields", fields
        )));
        return card;
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private byte[] canonicalJson(Map<String, Object> value) {
        return objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsBytes(value);
    }
}
