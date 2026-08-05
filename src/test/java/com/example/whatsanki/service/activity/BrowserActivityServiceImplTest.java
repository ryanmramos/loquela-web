package com.example.whatsanki.service.activity;

import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.OutboundMessageEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.ActivityItemDto;
import com.example.whatsanki.repository.ActivityProjectionRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.OutboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserActivityException;
import com.example.whatsanki.service.util.ActivityReferenceCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserActivityServiceImplTest {
    @Mock private UserIdentityService userIdentityService;
    @Mock private ActivityProjectionRepository activityProjectionRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private LookupSessionRepository lookupSessionRepository;
    @Mock private OutboundMessageRepository outboundMessageRepository;
    @Mock private ExportRequestRepository exportRequestRepository;
    @Mock private DeckRepository deckRepository;
    @InjectMocks private BrowserActivityServiceImpl service;

    private UserId userId;

    @BeforeEach
    void setUp() {
        userId = UserId.random();
        when(userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, "subject"))
                .thenReturn(Optional.of(userId));
    }

    @Test
    void returnsBoundedPageAndOpaqueContinuationCursor() {
        var first = row(UUID.randomUUID(), Instant.parse("2026-08-04T20:00:00Z"));
        var second = mock(ActivityProjectionRepository.ActivityRow.class);
        when(activityProjectionRepository.countActive(userId.value())).thenReturn(2L);
        when(activityProjectionRepository.findPage(
                userId.value(), null, null, null, null, null, 2
        )).thenReturn(List.of(first, second));

        var page = service.list("subject", null, null, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.activeCount()).isEqualTo(2);
        assertThat(page.nextCursor()).isNotBlank();
        assertThat(ActivityReferenceCodec.decodeCursor(page.nextCursor())).isPresent();
    }

    @Test
    void rejectsInvalidCursorBeforeQueryingPersistence() {
        assertThatThrownBy(() -> service.list("subject", null, null, "broken", 20))
                .isInstanceOfSatisfying(BrowserActivityException.class, exception ->
                        assertThat(exception.problemCode()).isEqualTo("INVALID_ACTIVITY_CURSOR"));
    }

    @Test
    void queryOmitsMalformedMissingAndCrossUserReferencesAndPreservesOrder() {
        UUID lookupId = UUID.randomUUID();
        String lookupReference = ActivityReferenceCodec.encode(ActivityItemDto.Category.LOOKUP, lookupId);
        LookupSessionEntity lookup = lookup(lookupId, false);
        when(lookupSessionRepository.findAllByUserIdAndIdIn(userId.value(), Set.of(lookupId)))
                .thenReturn(List.of(lookup));
        when(outboundMessageRepository.findAllByUserIdAndLookupSessionIdIn(userId.value(), Set.of(lookupId)))
                .thenReturn(List.of());
        when(activityProjectionRepository.countActive(userId.value())).thenReturn(2L);

        var result = service.queryStatuses("subject", List.of("malformed", lookupReference, lookupReference));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).reference()).isEqualTo(lookupReference);
        assertThat(result.items().get(0).deliveryStatus())
                .isEqualTo(ActivityItemDto.DeliveryStatus.NOT_APPLICABLE);
    }

    @Test
    void failedWhatsappDeliveryFailsTheEndToEndLookupOperation() {
        UUID lookupId = UUID.randomUUID();
        String reference = ActivityReferenceCodec.encode(ActivityItemDto.Category.LOOKUP, lookupId);
        LookupSessionEntity lookup = lookup(lookupId, true);
        OutboundMessageEntity outbound = new OutboundMessageEntity();
        outbound.setLookupSessionId(lookupId);
        outbound.setStatus(OutboundMessageEntity.Status.FAILED);
        outbound.setUpdatedAt(Instant.parse("2026-08-04T20:01:00Z"));
        when(lookupSessionRepository.findAllByUserIdAndIdIn(userId.value(), Set.of(lookupId)))
                .thenReturn(List.of(lookup));
        when(outboundMessageRepository.findAllByUserIdAndLookupSessionIdIn(userId.value(), Set.of(lookupId)))
                .thenReturn(List.of(outbound));
        when(activityProjectionRepository.countActive(userId.value())).thenReturn(2L);

        ActivityItemDto item = service.queryStatuses("subject", List.of(reference)).items().get(0);

        assertThat(item.status()).isEqualTo(ActivityItemDto.Status.READY);
        assertThat(item.deliveryStatus()).isEqualTo(ActivityItemDto.DeliveryStatus.FAILED);
        assertThat(item.lifecycle()).isEqualTo(ActivityItemDto.Lifecycle.FAILED);
        assertThat(item.updatedAt()).isEqualTo(outbound.getUpdatedAt());
    }

    private ActivityProjectionRepository.ActivityRow row(UUID id, Instant updatedAt) {
        var row = mock(ActivityProjectionRepository.ActivityRow.class);
        when(row.getId()).thenReturn(id);
        when(row.getCategory()).thenReturn("SOURCE");
        when(row.getOperation()).thenReturn("INGEST_SOURCE");
        when(row.getLifecycle()).thenReturn("ACTIVE");
        when(row.getStatus()).thenReturn("EXTRACTING");
        when(row.getResourceLabel()).thenReturn("Spanish notes");
        when(row.getCreatedAt()).thenReturn(updatedAt.minusSeconds(60));
        when(row.getUpdatedAt()).thenReturn(updatedAt);
        return row;
    }

    private LookupSessionEntity lookup(UUID id, boolean whatsapp) {
        LookupSessionEntity lookup = new LookupSessionEntity();
        lookup.setId(id);
        lookup.setInboundMessageId(whatsapp ? UUID.randomUUID() : null);
        lookup.setRequestedTerm("hablar");
        lookup.setStatus(LookupSessionEntity.Status.READY);
        lookup.setCreatedAt(Instant.parse("2026-08-04T20:00:00Z"));
        lookup.setUpdatedAt(Instant.parse("2026-08-04T20:00:30Z"));
        return lookup;
    }
}
