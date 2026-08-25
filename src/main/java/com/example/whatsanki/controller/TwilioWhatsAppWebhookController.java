package com.example.whatsanki.controller;

import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.twilio.TwiMLService;
import com.example.whatsanki.service.twilio.TwilioSignatureService;
import com.example.whatsanki.service.twilio.TwilioWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verifies Twilio signatures before translating WhatsApp form requests into commands. */
@ExternalEndpointSecurity(ExternalEndpointSecurity.Control.TWILIO_SIGNED_WEBHOOK)
@RestController
@RequestMapping("/webhooks/twilio")
@ConditionalOnProperty(prefix = "whatsanki.twilio", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsAppWebhookController {

    private static final String TWILIO_SIGNATURE_HEADER = "X-Twilio-Signature";

    private final TwilioWebhookService twilioWebhookService;
    private final TwilioSignatureService twilioSignatureService;
    private final TwiMLService twiMLService;

    @PostMapping(
            value = "/whatsapp",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> receiveMessage(
            HttpServletRequest servletRequest,
            @RequestHeader(value = TWILIO_SIGNATURE_HEADER, required = false) String signature,
            @Valid @ModelAttribute TwilioWebhookRequestDto request
    ) {
        if (!twilioSignatureService.isValid(servletRequest, signature)) {
            log.warn("Rejected Twilio WhatsApp webhook because signature validation failed");
            return ResponseEntity.status(403).build();
        }

        log.debug("Accepted signed Twilio WhatsApp webhook hasMedia={}", request.getNumMedia() > 0);
        TwilioWebhookResponseDto response = twilioWebhookService.handle(request);
        log.debug("Twilio WhatsApp webhook processed successfully");
        return ResponseEntity.ok(twiMLService.message(response.reply()));
    }
}
