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

    private String mediaUrl;

    private String mediaContentType;
}
