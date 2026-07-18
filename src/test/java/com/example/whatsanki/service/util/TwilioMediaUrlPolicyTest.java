package com.example.whatsanki.service.util;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.MediaAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwilioMediaUrlPolicyTest {

    private TwilioMediaUrlPolicy policy;

    @BeforeEach
    void setUp() {
        WhatsAnkiConfigurationDto configuration = new WhatsAnkiConfigurationDto();
        configuration.getTwilio().setAccountSid("AC11111111111111111111111111111111");
        policy = new TwilioMediaUrlPolicy(configuration);
    }

    @Test
    void permitsOnlyCanonicalHttpsMediaPathForConfiguredAccount() {
        URI valid = URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                + "AC11111111111111111111111111111111/Messages/"
                + "SM22222222222222222222222222222222/Media/ME33333333333333333333333333333333");

        assertThatCode(() -> policy.validate(valid)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSsrFAndCrossAccountVariants() {
        String path = "/2010-04-01/Accounts/AC11111111111111111111111111111111/Messages/"
                + "SM22222222222222222222222222222222/Media/ME33333333333333333333333333333333";
        assertInvalid("http://api.twilio.com" + path);
        assertInvalid("https://127.0.0.1" + path);
        assertInvalid("https://api.twilio.com/2010-04-01/Accounts/AC99999999999999999999999999999999/Messages/"
                + "SM22222222222222222222222222222222/Media/ME33333333333333333333333333333333");
        assertInvalid("https://api.twilio.com:8443" + path);
    }

    @Test
    void permitsOnlyExplicitHttpsRedirectHosts() {
        assertThatCode(() -> policy.validateRedirect(
                URI.create("https://mms.twiliocdn.com/file?signature=short-lived")
        )).doesNotThrowAnyException();

        assertInvalidRedirect("http://mms.twiliocdn.com/file");
        assertInvalidRedirect("https://evil.example/file");
        assertInvalidRedirect("https://sub.mms.twiliocdn.com/file");
        assertInvalidRedirect("https://user@mms.twiliocdn.com/file");
        assertInvalidRedirect("https://mms.twiliocdn.com:8443/file");
        assertInvalidRedirect("https://mms.twiliocdn.com/file#fragment");
        assertInvalidRedirect("/relative/path");
    }

    private void assertInvalid(String uri) {
        assertThatThrownBy(() -> policy.validate(URI.create(uri)))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo("INVALID_MEDIA_URL"));
    }

    private void assertInvalidRedirect(String uri) {
        assertThatThrownBy(() -> policy.validateRedirect(URI.create(uri)))
                .isInstanceOfSatisfying(MediaAcquisitionException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo("MEDIA_REDIRECT_REJECTED"));
    }
}
