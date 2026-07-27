package com.example.whatsanki.service.source;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.PastedTextAcceptanceDto;
import com.example.whatsanki.dto.PastedTextSourceDto;
import com.example.whatsanki.service.BrowserSourceService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.document.PastedTextIngestionService;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrowserSourceServiceImpl implements BrowserSourceService {

    private final UserIdentityService userIdentityService;
    private final PastedTextIngestionService pastedTextIngestionService;

    @Override
    public PastedTextSourceDto createPastedText(String googleSubject, String text) {
        UserId userId = userIdentityService
                .findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
        PastedTextAcceptanceDto accepted = pastedTextIngestionService.acceptWebsite(userId, text);
        return new PastedTextSourceDto(
                accepted.sourceId().value(),
                SourceReferenceCodec.encode(accepted.sourceId()),
                PastedTextSourceDto.Status.RECEIVED,
                accepted.characterCount()
        );
    }
}
