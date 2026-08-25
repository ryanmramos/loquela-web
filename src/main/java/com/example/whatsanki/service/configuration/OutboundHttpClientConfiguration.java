package com.example.whatsanki.service.configuration;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/** Builds outbound clients with redirects disabled at the transport boundary. */
@Configuration(proxyBeanMethods = false)
public class OutboundHttpClientConfiguration {

    @Bean
    HttpClient twilioMediaHttpClient(WhatsAnkiConfigurationDto configuration) {
        // One immutable client shares connection pools across jobs. Transport-level
        // redirects remain disabled so the downloader can validate Twilio's one CDN
        // hop and deliberately omit credentials from the follow-up request.
        return HttpClient.newBuilder()
                .connectTimeout(configuration.getIngestion().getMediaConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("wiktionaryHttpClient")
    HttpClient wiktionaryHttpClient(WhatsAnkiConfigurationDto configuration) {
        // Wikimedia redirects are not part of the adapter contract. Keeping them off
        // makes the fixed host allow-list meaningful and avoids following an arbitrary
        // location supplied by an upstream error page.
        return HttpClient.newBuilder()
                .connectTimeout(configuration.getDictionary().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("raeHttpClient")
    HttpClient raeHttpClient(WhatsAnkiConfigurationDto configuration) {
        // The API key is added per request by the adapter. Redirects remain disabled
        // so credentials cannot be forwarded to an origin outside the allow-list.
        return HttpClient.newBuilder()
                .connectTimeout(configuration.getDictionary().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
