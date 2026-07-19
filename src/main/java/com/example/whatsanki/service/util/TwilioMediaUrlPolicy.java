package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.dto.TwilioMediaReferenceDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TwilioMediaUrlPolicy {

    private static final Pattern MEDIA_RESOURCE = Pattern.compile(
            "((?:SM|MM)[0-9a-fA-F]{32})/Media/(ME[0-9a-fA-F]{32})"
    );

    private final WhatsAnkiConfigurationDto configuration;

    public void validate(URI mediaUri) {
        if (mediaUri == null
                || !"https".equalsIgnoreCase(mediaUri.getScheme())
                || mediaUri.getHost() == null
                || mediaUri.getUserInfo() != null
                || mediaUri.getFragment() != null) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_URL");
        }

        String host = mediaUri.getHost().toLowerCase(Locale.ROOT);
        boolean allowedHost = configuration.getTwilio().getMediaAllowedHosts().stream()
                .map(candidate -> candidate.toLowerCase(Locale.ROOT))
                .anyMatch(host::equals);
        if (!allowedHost || mediaUri.getPort() != -1) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_URL");
        }

        // Twilio media resource URLs are account-scoped. Requiring our configured
        // account in the canonical API path prevents the signed webhook from becoming
        // a server-side request forgery primitive or a cross-account credential oracle.
        String expectedPrefix = "/2010-04-01/Accounts/"
                + configuration.getTwilio().getAccountSid() + "/Messages/";
        String path = mediaUri.getRawPath();
        String resourcePath = path != null && path.startsWith(expectedPrefix)
                ? path.substring(expectedPrefix.length()) : "";
        if (!MEDIA_RESOURCE.matcher(resourcePath).matches()) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_URL");
        }
    }

    public TwilioMediaReferenceDto reference(URI mediaUri) {
        validate(mediaUri);
        String expectedPrefix = "/2010-04-01/Accounts/"
                + configuration.getTwilio().getAccountSid() + "/Messages/";
        Matcher matcher = MEDIA_RESOURCE.matcher(mediaUri.getRawPath().substring(expectedPrefix.length()));
        if (!matcher.matches()) {
            throw MediaAcquisitionException.permanent("INVALID_MEDIA_URL");
        }
        return new TwilioMediaReferenceDto(matcher.group(1), matcher.group(2));
    }

    public void validateRedirect(URI redirectUri) {
        if (redirectUri == null
                || !redirectUri.isAbsolute()
                || !"https".equalsIgnoreCase(redirectUri.getScheme())
                || redirectUri.getHost() == null
                || redirectUri.getUserInfo() != null
                || redirectUri.getFragment() != null
                || redirectUri.getPort() != -1) {
            throw MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED");
        }

        String host = redirectUri.getHost().toLowerCase(Locale.ROOT);
        boolean allowedHost = configuration.getTwilio().getMediaRedirectAllowedHosts().stream()
                .map(candidate -> candidate.toLowerCase(Locale.ROOT))
                .anyMatch(host::equals);
        if (!allowedHost) {
            throw MediaAcquisitionException.permanent("MEDIA_REDIRECT_REJECTED");
        }
    }
}
