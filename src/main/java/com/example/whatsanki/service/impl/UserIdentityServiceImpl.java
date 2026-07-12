package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserIdentityServiceImpl implements UserIdentityService {
    private final AppUserRepository appUserRepository;
    private final IdentityProtectionService identityProtectionService;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional
    public UserId resolveOrCreate(String rawWhatsAppAddress) {
        WhatsAppAddress address = new WhatsAppAddress(rawWhatsAppAddress);
        byte[] addressHash = identityProtectionService.hash(address);
        return appUserRepository.findByWhatsappAddressHash(addressHash)
                .map(user -> new UserId(user.getId()))
                .orElseGet(() -> create(address, addressHash));
    }

    private UserId create(WhatsAppAddress address, byte[] addressHash) {
        AppUserEntity user = new AppUserEntity();
        user.setWhatsappAddressHash(addressHash);
        user.setWhatsappAddressCiphertext(identityProtectionService.encrypt(address));
        user.setSourceLanguage(configuration.getLanguages().getDefaultSource());
        user.setDefinitionLanguage(configuration.getLanguages().getDefaultDefinition());
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }
}
