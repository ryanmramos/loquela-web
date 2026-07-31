package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserSettingsDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.UpdateBrowserSettingsRequestDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.BrowserSettingsService;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.LanguagePairSupport;
import com.example.whatsanki.service.exception.UnsupportedBrowserLanguagePairException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrowserSettingsServiceImpl implements BrowserSettingsService {
    private final UserIdentityRepository userIdentityRepository;
    private final AppUserRepository appUserRepository;
    private final IdentityProtectionService identityProtectionService;
    private final LanguagePairSupport languagePairSupport;

    @Override
    @Transactional(readOnly = true)
    public BrowserSettingsDto get(String googleSubject) {
        UserIdentityEntity identity = googleIdentity(googleSubject);
        return toDto(identity, activeUser(identity));
    }

    @Override
    @Transactional
    public BrowserSettingsDto update(String googleSubject, UpdateBrowserSettingsRequestDto request) {
        UserIdentityEntity identity = googleIdentity(googleSubject);
        AppUserEntity user = activeUser(identity);
        String sourceLanguage = new LanguageTag(request.sourceLanguage()).value();
        String definitionLanguage = new LanguageTag(request.definitionLanguage()).value();
        if (!languagePairSupport.supports(new LanguagePairDto(sourceLanguage, definitionLanguage))) {
            throw new UnsupportedBrowserLanguagePairException();
        }
        user.setSourceLanguage(sourceLanguage);
        user.setDefinitionLanguage(definitionLanguage);
        user.setAutomaticCaptionFallback(request.automaticCaptionFallback());
        user.setExportDeckPrefix(request.exportDeckPrefix() == null ? "" : request.exportDeckPrefix().strip());
        user.setExportIncludeProvenance(request.exportIncludeProvenance());
        return toDto(identity, appUserRepository.save(user));
    }

    private UserIdentityEntity googleIdentity(String subject) {
        return userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.GOOGLE, identityProtectionService.hashSubject(subject)
        ).orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
    }

    private AppUserEntity activeUser(UserIdentityEntity identity) {
        return appUserRepository.findById(identity.getUserId())
                .filter(user -> user.getStatus() == AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active authenticated user does not exist"));
    }

    private BrowserSettingsDto toDto(UserIdentityEntity identity, AppUserEntity user) {
        return new BrowserSettingsDto(
                new BrowserSettingsDto.ProfileDto(identity.getDisplayName(), identity.getDisplayEmail()),
                new BrowserSettingsDto.LinkedAccountsDto(
                        true,
                        identity.getCreatedAt(),
                        userIdentityRepository.existsByUserIdAndProvider(
                                user.getId(), UserIdentityEntity.Provider.WHATSAPP
                        )
                ),
                new LanguagePairDto(user.getSourceLanguage(), user.getDefinitionLanguage()),
                languagePairSupport.supportedPairs().stream()
                        .map(pair -> new LanguagePairDto(pair.sourceLanguage(), pair.definitionLanguage()))
                        .toList(),
                user.isAutomaticCaptionFallback(),
                user.getExportDeckPrefix(),
                user.isExportIncludeProvenance()
        );
    }
}
