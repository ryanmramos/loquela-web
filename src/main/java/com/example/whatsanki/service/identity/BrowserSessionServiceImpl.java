package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserSessionDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.BrowserSessionService;
import com.example.whatsanki.service.IdentityProtectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrowserSessionServiceImpl implements BrowserSessionService {

    private final UserIdentityRepository userIdentityRepository;
    private final AppUserRepository appUserRepository;
    private final IdentityProtectionService identityProtectionService;

    @Override
    @Transactional(readOnly = true)
    public BrowserSessionDto getByGoogleSubject(String googleSubject) {
        byte[] subjectHash = identityProtectionService.hashSubject(googleSubject);
        UserIdentityEntity googleIdentity = userIdentityRepository
                .findByProviderAndProviderSubjectHash(UserIdentityEntity.Provider.GOOGLE, subjectHash)
                .orElseThrow(() -> new IllegalStateException("Authenticated Google identity is not linked"));
        AppUserEntity user = appUserRepository
                .findByIdAndStatus(googleIdentity.getUserId(), AppUserEntity.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not active"));
        UserId userId = new UserId(user.getId());
        return new BrowserSessionDto(
                user.getId(),
                googleIdentity.getDisplayName(),
                googleIdentity.getDisplayEmail(),
                user.getSourceLanguage(),
                user.getDefinitionLanguage(),
                new BrowserSessionDto.LinkedAccountsDto(
                        true,
                        userIdentityRepository.existsByUserIdAndProvider(
                                userId.value(), UserIdentityEntity.Provider.WHATSAPP
                        )
                )
        );
    }
}
