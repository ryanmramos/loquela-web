package com.example.whatsanki.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwilioWebhookRequestDto {

    @NotBlank
    private String messageSid;

    @NotBlank
    private String from;

    @NotBlank
    private String to;

    private String body = "";

    @Min(0)
    private int numMedia;

    // Twilio indexes media form fields starting at zero.
    private String mediaUrl0;

    private String mediaContentType0;

    // Kept for local fixtures created before the Twilio-indexed field names were added.
    private String mediaUrl;

    private String mediaContentType;

    public String firstMediaUrl() {
        return mediaUrl0 != null ? mediaUrl0 : mediaUrl;
    }

    public String firstMediaContentType() {
        return mediaContentType0 != null ? mediaContentType0 : mediaContentType;
    }
}
