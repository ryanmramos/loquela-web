package com.example.whatsanki.service.configuration;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupReleaseLogger {
    private final WhatsAnkiConfigurationDto configuration;

    @EventListener(ApplicationReadyEvent.class)
    public void logReadyRelease() {
        log.info("Loquela application ready releaseId={}", configuration.getReleaseId());
    }
}
