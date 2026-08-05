package com.example.whatsanki.service.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BrowserCacheControlFilterTest {
    private final BrowserCacheControlFilter filter = new BrowserCacheControlFilter();

    @Test
    void preventsStorageForBrowserApiSuccessAndFailurePaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/session");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("Thu, 01 Jan 1970 00:00:00 GMT");
    }

    @Test
    void leavesNonApiCachingToTheServingEdge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/main.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("Cache-Control")).isNull();
    }
}
