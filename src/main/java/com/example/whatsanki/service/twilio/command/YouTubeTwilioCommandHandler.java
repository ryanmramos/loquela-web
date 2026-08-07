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
        try {
            YouTubeSourceAcceptanceDto result = youTubeSourceService.accept(
                    context.userId(), context.inboundMessageId(), context.command().argument()
            );
            log.info("Processed YOUTUBE command userId={} sourceId={} existing={}",
                    context.userId().value(), result.sourceId().value(), result.existing());
            return new TwilioWebhookResponseDto(ACCEPTED);
        } catch (YouTubeIngestionDisabledException exception) {
            log.info("Rejected YOUTUBE command userId={} errorCode=YOUTUBE_INGESTION_DISABLED",
                    context.userId().value());
            return new TwilioWebhookResponseDto(DISABLED);
        } catch (IllegalArgumentException exception) {
            log.info("Rejected YOUTUBE command userId={} errorCode=INVALID_YOUTUBE_URL",
                    context.userId().value());
            return new TwilioWebhookResponseDto(INVALID);
        }
    }
}
