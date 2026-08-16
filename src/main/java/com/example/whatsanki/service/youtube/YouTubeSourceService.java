package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.YouTubeSourceAcceptanceDto;

@Deprecated(forRemoval = true)
public interface YouTubeSourceService {
    YouTubeSourceAcceptanceDto accept(UserId userId, InboundMessageId inboundMessageId, String rawUrl);

    YouTubeSourceAcceptanceDto acceptWebsite(UserId userId, String rawUrl);
}
