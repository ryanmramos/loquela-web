package com.example.whatsanki.controller;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesSafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/twilio/whatsapp");
        request.addHeader(RequestCorrelationFilter.CORRELATION_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.CORRELATION_HEADER)).isEqualTo("request-123");
        assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(RequestCorrelationFilter.CORRELATION_HEADER, "unsafe value with spaces\nforged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestCorrelationFilter.CORRELATION_HEADER))
                .isNotBlank()
                .doesNotContain("unsafe")
                .matches("[0-9a-f-]{36}");
        assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_MDC_KEY)).isNull();
    }
}
