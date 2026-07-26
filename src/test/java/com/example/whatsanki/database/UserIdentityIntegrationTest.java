package com.example.whatsanki.database;

import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.service.IdentityProtectionService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.card.DefaultDeckService;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdentityIntegrationTest extends PostgreSqlIntegrationTest {

    private final UserIdentityService userIdentityService;
    private final UserIdentityRepository userIdentityRepository;
    private final AppUserRepository appUserRepository;
    private final DeckRepository deckRepository;
    private final IdentityProtectionService identityProtectionService;

    UserIdentityIntegrationTest(
            UserIdentityService userIdentityService,
            UserIdentityRepository userIdentityRepository,
            AppUserRepository appUserRepository,
            DeckRepository deckRepository,
            IdentityProtectionService identityProtectionService
    ) {
        this.userIdentityService = userIdentityService;
        this.userIdentityRepository = userIdentityRepository;
        this.appUserRepository = appUserRepository;
        this.deckRepository = deckRepository;
        this.identityProtectionService = identityProtectionService;
    }

    @Test
    @Transactional
    void createsFullyFunctionalGoogleOnlyUser() {
        UserId userId = userIdentityService.resolveOrCreateGoogle(
                "google-only-subject",
                "learner@example.com",
                "Web Learner"
        );

        assertThat(appUserRepository.findById(userId.value())).isPresent();
        assertThat(userIdentityService.hasIdentity(userId, UserIdentityEntity.Provider.GOOGLE)).isTrue();
        assertThat(userIdentityService.hasIdentity(userId, UserIdentityEntity.Provider.WHATSAPP)).isFalse();
        assertThat(deckRepository.findByUserIdAndNormalizedName(
                userId.value(),
                DefaultDeckService.DEFAULT_DECK_NORMALIZED_NAME
        )).isPresent();
    }

    @Test
    @Transactional
    void oneUserCanOwnGoogleAndWhatsAppIdentities() {
        UserId userId = userIdentityService.resolveOrCreateGoogle(
                "dual-identity-subject",
                "dual@example.com",
                "Dual Identity"
        );

        userIdentityService.linkWhatsApp(userId, "whatsapp:+15550000101");

        assertThat(userIdentityService.hasIdentity(userId, UserIdentityEntity.Provider.GOOGLE)).isTrue();
        assertThat(userIdentityService.hasIdentity(userId, UserIdentityEntity.Provider.WHATSAPP)).isTrue();
        assertThat(userIdentityService.requireWhatsAppAddress(userId).value())
                .isEqualTo("whatsapp:+15550000101");
    }

    @Test
    @Transactional
    void duplicateProviderSubjectCannotBeLinkedToTwoUsers() {
        UserId first = userIdentityService.resolveOrCreateGoogle("link-owner-one", null, null);
        UserId second = userIdentityService.resolveOrCreateGoogle("link-owner-two", null, null);
        userIdentityService.linkWhatsApp(first, "whatsapp:+15550000102");

        assertThatThrownBy(() ->
                userIdentityService.linkWhatsApp(second, "whatsapp:+15550000102"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another user");

        byte[] hash = identityProtectionService.hashSubject("whatsapp:+15550000102");
        assertThat(userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.WHATSAPP,
                hash
        )).get().extracting(UserIdentityEntity::getUserId).isEqualTo(first.value());
    }
}
