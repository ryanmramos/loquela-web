package com.example.whatsanki.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "whatsanki")
public class TwilioConfigurationDto {

    private String publicBaseUrl = "http://localhost:8080";
    private Twilio twilio = new Twilio();

    @Getter
    @Setter
    public static class Twilio {
        private String authToken = "";
    }
}
