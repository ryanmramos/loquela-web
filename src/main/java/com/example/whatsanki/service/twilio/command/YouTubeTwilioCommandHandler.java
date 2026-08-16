package com.example.whatsanki.service.twilio.command;

import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.dto.YouTubeSourceAcceptanceDto;
import com.example.whatsanki.service.TwilioCommandHandler;
import com.example.whatsanki.service.exception.YouTubeIngestionDisabledException;
import com.example.whatsanki.service.youtube.YouTubeSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated(forRemoval = true)
public class YouTubeTwilioCommandHandler implements TwilioCommandHandler {
    private static final String ACCEPTED =
            "YouTube captions accepted for indexing. Send *STATUS* to check progress.";
    private static final String INVALID =
            "Send *YOUTUBE* followed by one HTTPS youtube.com or youtu.be video URL. Playlists are not supported.";
    private static final String DISABLED =
            "YouTube caption ingestion is temporarily unavailable. Please try again later.";

    private final YouTubeSourceService youTubeSourceService;

    @Override
    public InboundMessageEntity.CommandType supportedCommand() {
        return InboundMessageEntity.CommandType.YOUTUBE;
    }

    @Override
    public TwilioWebhookResponseDto handle(TwilioCommandContextDto context) {
        log.info("Rejected retired YOUTUBE command userId={} errorCode=YOUTUBE_INGESTION_RETIRED",
                context.userId().value());
        return new TwilioWebhookResponseDto(
                "Direct YouTube ingestion has been retired. Upload a VTT or SRT file in the Loquela website."
        );
    }
}
