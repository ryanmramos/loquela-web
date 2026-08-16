package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.document.PastedTextIngestionService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.VideoSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserSourceServiceImplTest {

    @Mock private UserIdentityService userIdentityService;
    @Mock private PastedTextIngestionService pastedTextIngestionService;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private PassageRepository passageRepository;
    @Mock private VideoSourceRepository videoSourceRepository;
    @InjectMocks private BrowserSourceServiceImpl service;

    @Test
    void resolvesTheAuthenticatedSubjectAndUsesTheSharedPastedTextService() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
        when(pastedTextIngestionService.acceptWebsite(userId, "Texto para aprender."))
                .thenReturn(new PastedTextAcceptanceDto(sourceId, 20));

        var result = service.createPastedText("subject", "Texto para aprender.");

        assertThat(result.sourceId()).isEqualTo(sourceId.value());
        assertThat(result.reference()).isEqualTo(SourceReferenceCodec.encode(sourceId));
        assertThat(result.status().name()).isEqualTo("RECEIVED");
        verify(pastedTextIngestionService).acceptWebsite(userId, "Texto para aprender.");
    }

    @Test
    void reportsTotalWordsAndDatabaseBytesForTheOwnedSource() {
        UserId userId = UserId.random();
        SourceId sourceId = SourceId.random();
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(sourceId.value());
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.YOUTUBE_VIDEO);
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setDisplayName("Spanish lesson");
        source.setLanguage("es");
        source.setCreatedAt(java.time.Instant.parse("2026-07-27T12:00:00Z"));
        source.setUpdatedAt(java.time.Instant.parse("2026-07-27T12:01:00Z"));

        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
        when(sourceDocumentRepository.findByIdAndUserId(sourceId.value(), userId.value()))
                .thenReturn(Optional.of(source));
        when(passageRepository.totalWordCountByUserIdAndSourceId(userId.value(), sourceId.value()))
                .thenReturn(42L);
        when(sourceDocumentRepository.storedBytesByIdAndUserId(sourceId.value(), userId.value())).thenReturn(128L);
        when(videoSourceRepository.findBySourceIdAndUserId(sourceId.value(), userId.value())).thenReturn(Optional.empty());

        var result = service.get("subject", SourceReferenceCodec.encode(sourceId));

        assertThat(result.totalWords()).isEqualTo(42);
        assertThat(result.databaseSizeBytes()).isEqualTo(128);
    }
}
