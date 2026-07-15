package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.InboundMessageEntity;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.ParsedTwilioCommandDto;
import com.example.whatsanki.dto.TwilioCommandContextDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.InboundMessageRepository;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.BackgroundJobDispatcher;
import com.example.whatsanki.service.BackgroundJobService;
import com.example.whatsanki.service.impl.AddTwilioCommandHandler;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PastedTextAddIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final PassageRepository passageRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobDispatcher backgroundJobDispatcher;
    private final AddTwilioCommandHandler addTwilioCommandHandler;

    PastedTextAddIntegrationTest(
            AppUserRepository appUserRepository,
            InboundMessageRepository inboundMessageRepository,
            SourceDocumentRepository sourceDocumentRepository,
            PassageRepository passageRepository,
            BackgroundJobRepository backgroundJobRepository,
            BackgroundJobService backgroundJobService,
            BackgroundJobDispatcher backgroundJobDispatcher,
            AddTwilioCommandHandler addTwilioCommandHandler
    ) {
        this.appUserRepository = appUserRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.passageRepository = passageRepository;
        this.backgroundJobRepository = backgroundJobRepository;
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobDispatcher = backgroundJobDispatcher;
        this.addTwilioCommandHandler = addTwilioCommandHandler;
    }

    @Test
    void addIsIdempotentAndProducesReadyUserScopedSearchablePassages() {
        UserId owner = user("add-owner");
        UserId otherUser = user("add-other");
        InboundMessageId inboundMessageId = inboundMessage(owner);
        TwilioCommandContextDto context = context(
                owner,
                inboundMessageId,
                "El árbol está aquí. Tiene hojas verdes.\n\nLa niña lee debajo del árbol."
        );

        assertThat(addTwilioCommandHandler.handle(context).reply())
                .isEqualTo(AddTwilioCommandHandler.ACCEPTED_RESPONSE);
        assertThat(addTwilioCommandHandler.handle(context).reply())
                .isEqualTo(AddTwilioCommandHandler.ACCEPTED_RESPONSE);

        var sources = sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(owner.value());
        assertThat(sources).hasSize(1);
        SourceDocumentEntity source = sources.get(0);
        assertThat(source.getStatus()).isEqualTo(SourceDocumentEntity.Status.RECEIVED);
        assertThat(source.getInboundMessageId()).isEqualTo(inboundMessageId.value());

        var jobs = backgroundJobService.findRecent(owner, 10);
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.type()).isEqualTo(BackgroundJobEntity.Type.INGEST_TEXT);
            assertThat(job.state()).isEqualTo(BackgroundJobEntity.State.QUEUED);
        });
        BackgroundJobEntity job = backgroundJobRepository
                .findByIdAndUserId(jobs.get(0).id(), owner.value())
                .orElseThrow();

        // Dispatch directly so this test exercises the registered handler without racing
        // the globally scheduled queue. Job-runner leasing itself is covered separately.
        backgroundJobDispatcher.dispatch(new ClaimedBackgroundJobDto(
                new JobId(job.getId()),
                owner.value(),
                job.getType(),
                job.getPayload(),
                1,
                job.getMaxAttempts()
        ));
        job.setState(BackgroundJobEntity.State.SUCCEEDED);
        job.setAttempts(1);
        backgroundJobRepository.save(job);

        SourceDocumentEntity ready = sourceDocumentRepository
                .findByIdAndUserId(source.getId(), owner.value())
                .orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(SourceDocumentEntity.Status.READY);
        assertThat(ready.getPassageCount()).isEqualTo(2);
        assertThat(ready.getCharacterCount()).isPositive();

        var passages = passageRepository.findAllByUserIdAndSourceIdOrderByOrdinal(owner.value(), source.getId());
        assertThat(passages).extracting(passage -> passage.getOrdinal()).containsExactly(0, 1);
        assertThat(passages).extracting(passage -> passage.getDisplayText()).containsExactly(
                "El árbol está aquí. Tiene hojas verdes.",
                "La niña lee debajo del árbol."
        );
        assertThat(passages).extracting(passage -> passage.getNormalizedText()).containsExactly(
                "el árbol está aquí. tiene hojas verdes.",
                "la niña lee debajo del árbol."
        );
        assertThat(passages).extracting(passage -> passage.getLocation().get("paragraphIndex"))
                .containsExactly(0, 1);
        assertThat(inboundMessageRepository.findByIdAndUserId(inboundMessageId.value(), owner.value()))
                .get()
                .extracting(InboundMessageEntity::getBodyCiphertext)
                .isNull();

        assertThat(passageRepository.searchReadyPassages(owner.value(), "árbol", PageRequest.of(0, 10)))
                .hasSize(2);
        assertThat(passageRepository.searchReadyPassages(owner.value(), "árb", PageRequest.of(0, 10)))
                .isEmpty();
        assertThatCode(() -> passageRepository.searchReadyPassages(
                owner.value(), "árbol & ! |", PageRequest.of(0, 10)
        )).doesNotThrowAnyException();
        assertThat(passageRepository.searchReadyPassages(otherUser.value(), "árbol", PageRequest.of(0, 10)))
                .isEmpty();
        assertThat(sourceDocumentRepository.findByIdAndUserId(source.getId(), otherUser.value())).isEmpty();

        SourceDocumentEntity receivedSource = receivedSource(owner);
        PassageEntity hiddenPassage = new PassageEntity();
        hiddenPassage.setUserId(owner.value());
        hiddenPassage.setSourceId(receivedSource.getId());
        hiddenPassage.setOrdinal(0);
        hiddenPassage.setDisplayText("Contenido oculto.");
        hiddenPassage.setNormalizedText("contenido oculto.");
        hiddenPassage.setLocation(new LinkedHashMap<>());
        hiddenPassage.setTokenCount(2);
        passageRepository.save(hiddenPassage);
        assertThat(passageRepository.searchReadyPassages(owner.value(), "oculto", PageRequest.of(0, 10)))
                .isEmpty();
    }

    private UserId user(String label) {
        String identity = label + "-" + UUID.randomUUID();
        AppUserEntity user = new AppUserEntity();
        user.setWhatsappAddressHash(identity.getBytes(StandardCharsets.UTF_8));
        user.setWhatsappAddressCiphertext(("encrypted-" + identity).getBytes(StandardCharsets.UTF_8));
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return new UserId(appUserRepository.saveAndFlush(user).getId());
    }

    private InboundMessageId inboundMessage(UserId userId) {
        InboundMessageEntity message = new InboundMessageEntity();
        message.setTwilioMessageSid("SM" + UUID.randomUUID().toString().replace("-", ""));
        message.setUserId(userId.value());
        message.setCommandType(InboundMessageEntity.CommandType.ADD);
        message.setReceivedAt(Instant.now());
        message.setPurgeAfter(Instant.now().plusSeconds(3_600));
        return new InboundMessageId(inboundMessageRepository.save(message).getId());
    }

    private SourceDocumentEntity receivedSource(UserId userId) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setUserId(userId.value());
        source.setKind(SourceDocumentEntity.Kind.PASTED_TEXT);
        source.setDisplayName("Not ready");
        source.setStatus(SourceDocumentEntity.Status.RECEIVED);
        source.setLanguage("es");
        source.setMetadata(new LinkedHashMap<>());
        return sourceDocumentRepository.save(source);
    }

    private TwilioCommandContextDto context(UserId userId, InboundMessageId inboundMessageId, String text) {
        return new TwilioCommandContextDto(
                userId,
                inboundMessageId,
                new ParsedTwilioCommandDto(
                        InboundMessageEntity.CommandType.ADD,
                        text,
                        text.toLowerCase(),
                        null,
                        null
                )
        );
    }
}
