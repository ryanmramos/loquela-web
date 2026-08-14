package com.example.whatsanki.service.security;

import com.example.whatsanki.service.demo.DemoWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BrowserAuthenticationSuccessHandlerTest {

    private final DemoWorkspaceService demoWorkspaceService = mock(DemoWorkspaceService.class);
    private final BrowserAuthenticationSuccessHandler handler =
            new BrowserAuthenticationSuccessHandler(
                    URI.create("http://localhost:4200"), demoWorkspaceService);

    @Test
    void consumesTheValidatedReturnPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(BrowserReturnPath.SESSION_ATTRIBUTE, "/app/cards");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                UsernamePasswordAuthenticationToken.authenticated("subject", null, List.of())
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/app/cards");
        assertThat(request.getSession().getAttribute(BrowserReturnPath.SESSION_ATTRIBUTE)).isNull();
        assertThat(request.getSession().getAttribute(
                BrowserAuthenticationSuccessHandler.RECENT_AUTHENTICATION_AT
        )).isInstanceOf(Long.class);
        verify(demoWorkspaceService).cleanupForSignIn(request.getSession());
    }

    @Test
    void fallsBackWhenTheSessionValueIsUnsafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(
                BrowserReturnPath.SESSION_ATTRIBUTE,
                "https://evil.example/steal"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                UsernamePasswordAuthenticationToken.authenticated("subject", null, List.of())
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/app");
    }
}
