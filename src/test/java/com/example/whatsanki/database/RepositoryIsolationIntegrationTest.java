package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.repository.UserIdentityRepository;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryIsolationIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final EntityManager entityManager;

    RepositoryIsolationIntegrationTest(
            AppUserRepository appUserRepository,
            UserIdentityRepository userIdentityRepository,
            InboundMessageRepository inboundMessageRepository,
            SourceDocumentRepository sourceDocumentRepository,
            PassageRepository passageRepository,
            EntityManager entityManager
    ) {
        this.appUserRepository = appUserRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.passageRepository = passageRepository;
        this.entityManager = entityManager;
    }

    @Test
    @Transactional
    void rejectsDuplicateWhatsAppIdentityHash() {
        byte[] hash = "same-hash".getBytes(StandardCharsets.UTF_8);
        AppUserEntity first = appUserRepository.saveAndFlush(user("first"));
        AppUserEntity second = appUserRepository.saveAndFlush(user("second"));
        userIdentityRepository.saveAndFlush(identity(first.getId(), hash, "ciphertext-1"));

        assertThatThrownBy(() -> userIdentityRepository.saveAndFlush(
                identity(second.getId(), hash, "ciphertext-2")
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void insertsWhatsAppIdentityOnlyOnceWhenHashConflicts() {
        byte[] hash = "upsert-hash".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.now();

        AppUserEntity first = appUserRepository.saveAndFlush(user("first-upsert"));
        AppUserEntity second = appUserRepository.saveAndFlush(user("second-upsert"));
        int firstInsert = userIdentityRepository.insertIfAbsent(
                UUID.randomUUID(), first.getId(), "WHATSAPP", hash,
                "ciphertext-1".getBytes(StandardCharsets.UTF_8), null, null, now
        );
        int duplicateInsert = userIdentityRepository.insertIfAbsent(
                UUID.randomUUID(), second.getId(), "WHATSAPP", hash,
                "ciphertext-2".getBytes(StandardCharsets.UTF_8), null, null, now
        );

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(userIdentityRepository.findByProviderAndProviderSubjectHash(
                UserIdentityEntity.Provider.WHATSAPP, hash
        )).isPresent();
    }

    @Test
    @Transactional
    void rejectsDuplicateTwilioMessageSid() {
        AppUserEntity user = appUserRepository.saveAndFlush(user("message-owner"));
        inboundMessageRepository.save(message(user.getId(), "SM00000000000000000000000000000001"));
        entityManager.flush();

        inboundMessageRepository.save(message(user.getId(), "SM00000000000000000000000000000001"));
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    @Transactional
    void insertsInboundMessageOnlyOnceWhenSidConflicts() {
        AppUserEntity user = appUserRepository.saveAndFlush(user("idempotent-message-owner"));
        String messageSid = "SM00000000000000000000000000000002";
        Instant receivedAt = Instant.now();

        int firstInsert = inboundMessageRepository.insertIfAbsent(
                UUID.randomUUID(), messageSid, user.getId(), "HELP", receivedAt, receivedAt.plusSeconds(60)
        );
        int duplicateInsert = inboundMessageRepository.insertIfAbsent(
                UUID.randomUUID(), messageSid, user.getId(), "HELP", receivedAt, receivedAt.plusSeconds(60)
        );

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(inboundMessageRepository.findByTwilioMessageSid(messageSid)).isPresent();
    }

    @Test
    @Transactional
    void scopesSourcesAndPassagesToTheirOwner() {
        AppUserEntity owner = appUserRepository.saveAndFlush(user("owner"));
        AppUserEntity otherUser = appUserRepository.saveAndFlush(user("other"));
        SourceDocumentEntity source = sourceDocumentRepository.save(source(owner.getId()));
        entityManager.flush();
        PassageEntity passage = passageRepository.save(passage(owner.getId(), source.getId()));
        entityManager.flush();

        assertThat(sourceDocumentRepository.findByIdAndUserId(source.getId(), owner.getId())).isPresent();
        assertThat(sourceDocumentRepository.findByIdAndUserId(source.getId(), otherUser.getId())).isEmpty();
        assertThat(passageRepository.findByIdAndUserId(passage.getId(), owner.getId())).isPresent();
        assertThat(passageRepository.findByIdAndUserId(passage.getId(), otherUser.getId())).isEmpty();
        assertThat(passageRepository.findAllByUserIdAndSourceIdOrderByOrdinal(otherUser.getId(), source.getId()))
                .isEmpty();
    }

    private AppUserEntity user(String identity) {
        AppUserEntity user = new AppUserEntity();
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return user;
    }

    private UserIdentityEntity identity(UUID userId, byte[] hash, String ciphertext) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(userId);
        identity.setProvider(UserIdentityEntity.Provider.WHATSAPP);
        identity.setProviderSubjectHash(hash);
        identity.setProviderSubjectCiphertext(ciphertext.getBytes(StandardCharsets.UTF_8));
        identity.setVerifiedAt(Instant.now());
        return identity;
    }

    private InboundMessageEntity message(UUID userId, String sid) {
        InboundMessageEntity message = new InboundMessageEntity();
        message.setUserId(userId);
        message.setTwilioMessageSid(sid);
        message.setCommandType(InboundMessageEntity.CommandType.HELP);
        message.setReceivedAt(Instant.now());
        return message;
    }

    private SourceDocumentEntity source(UUID userId) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(userId);
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setDisplayName("Test source");
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setLanguage("es");
        source.setCharacterCount(20L);
        source.setPassageCount(1);
        return source;
    }

    private PassageEntity passage(UUID userId, UUID sourceId) {
        PassageEntity passage = new PassageEntity();
        passage.setUserId(userId);
        passage.setSourceId(sourceId);
        passage.setOrdinal(0);
        passage.setDisplayText("Quiero vivir aquí.");
        passage.setNormalizedText("quiero vivir aquí");
        passage.setTokenCount(3);
        return passage;
    }
}
