package com.example.whatsanki.service.demo;

import jakarta.servlet.http.HttpServletRequest;

/** Extension point for a bot challenge without changing the public controller contract. */
public interface DemoBotVerificationService {
    void verify(HttpServletRequest request);
}
