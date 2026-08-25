package com.example.whatsanki.service.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Publishes the same-origin, session-authenticated browser API contract. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    static final String SESSION_COOKIE_SCHEME = "browserSession";

    @Bean
    OpenAPI loquelaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Loquela Browser API")
                        .version("v1")
                        .description("""
                                Authenticated, DTO-only browser contracts under /api/v1. Twilio webhooks are
                                intentionally excluded. Enums use string values. Collection endpoints use opaque
                                cursor pagination. Clients may send X-Correlation-ID; the server always returns the
                                effective value. Retriable create commands use Idempotency-Key. Versioned mutations
                                use If-Match and return the new ETag.
                                """))
                // Generated browser clients must remain same-origin in every environment.
                .servers(List.of(new Server().url("/").description("Same-origin browser API")))
                .components(new Components().addSecuritySchemes(
                        SESSION_COOKIE_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("LOQUELA_SESSION")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE_SCHEME));
    }

    @Bean
    GroupedOpenApi browserApi() {
        return GroupedOpenApi.builder()
                .group("browser-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
