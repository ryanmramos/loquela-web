package com.example.whatsanki.service;

import com.example.whatsanki.dto.AdminAccessEntryDto;
import com.example.whatsanki.dto.AdminAccessEntryListDto;
import com.example.whatsanki.dto.AdminAccessRole;
import com.example.whatsanki.dto.AdminAccessStatus;
import com.example.whatsanki.dto.CreateAdminAccessEntryRequestDto;
import com.example.whatsanki.dto.UpdateAdminAccessEntryRequestDto;

import java.util.UUID;

/** Manages the persistent Google access allowlist and its audit trail. */
public interface AdminAccessService {
    AdminAccessEntryListDto list(
            String googleSubject,
            String search,
            AdminAccessRole role,
            AdminAccessStatus status,
            int page,
            int pageSize
    );

    AdminAccessEntryDto create(
            String googleSubject,
            CreateAdminAccessEntryRequestDto request,
            boolean recentlyAuthenticated
    );

    AdminAccessEntryDto update(
            String googleSubject,
            UUID reference,
            UpdateAdminAccessEntryRequestDto request,
            boolean recentlyAuthenticated
    );
}
