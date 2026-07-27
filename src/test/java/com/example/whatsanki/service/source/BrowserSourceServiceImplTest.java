package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.document.PastedTextIngestionService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
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
}
