package com.example.whatsanki.controller;

import com.example.whatsanki.dto.TwilioWebhookRequestDto;
import com.example.whatsanki.dto.TwilioWebhookResponseDto;
import com.example.whatsanki.service.TwiMLService;
import com.example.whatsanki.service.TwilioSignatureService;
import com.example.whatsanki.service.TwilioWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TwilioWhatsAppWebhookControllerTest {

    @Mock
    private TwilioWebhookService twilioWebhookService;

    @Mock
    private TwilioSignatureService twilioSignatureService;

    @Mock
    private TwiMLService twiMLService;

    @InjectMocks
    private TwilioWhatsAppWebhookController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsHelpTwimlForValidSignedWebhook() throws Exception {
        when(twilioSignatureService.isValid(any(HttpServletRequest.class), eq("valid-signature")))
                .thenReturn(true);
        when(twilioWebhookService.handle(any(TwilioWebhookRequestDto.class)))
                .thenReturn(new TwilioWebhookResponseDto("WhatsAnki commands:\nHELP"));
        when(twiMLService.message("WhatsAnki commands:\nHELP"))
                .thenReturn("<Response><Message>WhatsAnki commands:\nHELP</Message></Response>");

        mockMvc.perform(post("/webhooks/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-Twilio-Signature", "valid-signature")
                        .param("MessageSid", "SM123")
                        .param("From", "whatsapp:+15550000001")
                        .param("To", "whatsapp:+15550000002")
                        .param("Body", "HELP")
                        .param("NumMedia", "0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string("<Response><Message>WhatsAnki commands:\nHELP</Message></Response>"));

        ArgumentCaptor<TwilioWebhookRequestDto> requestCaptor =
                ArgumentCaptor.forClass(TwilioWebhookRequestDto.class);
        verify(twilioWebhookService).handle(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMessageSid()).isEqualTo("SM123");
        assertThat(requestCaptor.getValue().getFrom()).isEqualTo("whatsapp:+15550000001");
        assertThat(requestCaptor.getValue().getBody()).isEqualTo("HELP");
    }

    @Test
    void rejectsInvalidSignatureWithoutCallingBusinessService() throws Exception {
        when(twilioSignatureService.isValid(any(HttpServletRequest.class), eq("invalid-signature")))
                .thenReturn(false);

        mockMvc.perform(post("/webhooks/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-Twilio-Signature", "invalid-signature")
                        .param("MessageSid", "SM123")
                        .param("From", "whatsapp:+15550000001")
                        .param("To", "whatsapp:+15550000002")
                        .param("Body", "HELP"))
                .andExpect(status().isForbidden());

        verify(twilioWebhookService, never()).handle(any());
    }

    @Test
    void rejectsWebhookMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/webhooks/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-Twilio-Signature", "valid-signature")
                        .param("Body", "HELP"))
                .andExpect(status().isBadRequest());

        verify(twilioWebhookService, never()).handle(any());
    }
}
