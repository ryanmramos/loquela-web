package com.example.whatsanki.service;

import com.example.whatsanki.domain.UserId;

public interface UserIdentityService {
    UserId resolveOrCreate(String rawWhatsAppAddress);
}
