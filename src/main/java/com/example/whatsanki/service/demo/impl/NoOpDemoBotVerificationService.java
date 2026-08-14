package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.service.demo.DemoBotVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class NoOpDemoBotVerificationService implements DemoBotVerificationService {
    @Override
    public void verify(HttpServletRequest request) {
        // Rate limits and feature switches are the initial launch controls.
    }
}
