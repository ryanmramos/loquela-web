package com.example.whatsanki.service.identity;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.BrowserSessionDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.BrowserSessionService;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.exception.BrowserAccountUnavailableException;
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
        AppUserEntity user = findUser(googleSubject);
        UserIdentityEntity googleIdentity = findGoogleIdentity(googleSubject);
        return toSession(googleIdentity, user);
    }

    @Override
    @Transactional
    public BrowserSessionDto updateInterfaceLanguage(String googleSubject, String interfaceLanguage) {
        AppUserEntity user = findUser(googleSubject);
        user.setInterfaceLanguage(interfaceLanguage);
        return toSession(findGoogleIdentity(googleSubject), user);
    }

    private AppUserEntity findUser(String googleSubject) {
        byte[] subjectHash = identityProtectionService.hashSubject(googleSubject);
        UserIdentityEntity googleIdentity = userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.GOOGLE, subjectHash
        ).orElseThrow(() -> new BrowserAccountUnavailableException("ACCOUNT_DELETED"));
        AppUserEntity user = appUserRepository.findById(googleIdentity.getUserId())
                .orElseThrow(() -> new BrowserAccountUnavailableException("ACCOUNT_DELETED"));
        if (user.getStatus() == AppUserEntity.Status.DISABLED) {
            throw new BrowserAccountUnavailableException("ACCOUNT_DISABLED");
        }
        return user;
    }

    private UserIdentityEntity findGoogleIdentity(String googleSubject) {
        return userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.GOOGLE, identityProtectionService.hashSubject(googleSubject)
        ).orElseThrow(() -> new BrowserAccountUnavailableException("ACCOUNT_DELETED"));
    }

    private BrowserSessionDto toSession(UserIdentityEntity googleIdentity, AppUserEntity user) {
        UserId userId = new UserId(user.getId());
        return new BrowserSessionDto(
                user.getId(),
                googleIdentity.getDisplayName(),
                googleIdentity.getDisplayEmail(),
                user.getSourceLanguage(),
                user.getDefinitionLanguage(),
                user.getInterfaceLanguage(),
                user.getStatus() == AppUserEntity.Status.DELETING
                        ? BrowserSessionDto.AccountState.DELETING
                        : BrowserSessionDto.AccountState.ACTIVE,
                new BrowserSessionDto.LinkedAccountsDto(
                        true,
                        userIdentityRepository.existsByUserIdAndProvider(
                                userId.value(), UserIdentityEntity.Provider.WHATSAPP
                        )
                )
        );
    }
}
