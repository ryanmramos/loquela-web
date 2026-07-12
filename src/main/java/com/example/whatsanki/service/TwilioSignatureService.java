package com.example.whatsanki.service;

import jakarta.servlet.http.HttpServletRequest;

public interface TwilioSignatureService {
    boolean isValid(HttpServletRequest request, String signature);
}
