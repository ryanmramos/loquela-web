package com.example.whatsanki.service.impl;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.TwilioSignatureService;
import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSignatureServiceImpl implements TwilioSignatureService {

    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public boolean isValid(HttpServletRequest request, String signature) {
        String authToken = configuration.getTwilio().getAuthToken();
        if (authToken.isBlank()) {
            log.error("Twilio signature validation is unavailable because the Auth Token is not configured");
            return false;
        }
        if (signature == null || signature.isBlank()) {
            log.debug("Twilio signature header is missing");
            return false;
        }
        RequestValidator validator = new RequestValidator(authToken);
        boolean valid = validator.validate(externalRequestUrl(request), signatureParameters(request), signature);
        if (!valid) {
            log.debug("Twilio request signature did not match");
        }
        return valid;
    }

    private String externalRequestUrl(HttpServletRequest request) {
        String base = configuration.getPublicBaseUrl().toString().replaceFirst("/$", "");
        StringBuilder url = new StringBuilder(base).append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

    private Map<String, String> signatureParameters(HttpServletRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values != null && values.length > 0) {
                parameters.put(name, values[0]);
            }
        });
        return parameters;
    }
}
