package com.example.whatsanki.database;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.CardSaveResultDto;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryProvenanceDto;
import com.example.whatsanki.dto.DictionarySenseDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupResultPayloadDto;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.CardRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.card.CardSaveService;
import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CardSaveIntegrationTest extends PostgreSqlIntegrationTest {

    private final AppUserRepository appUserRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final LookupSessionRepository lookupSessionRepository;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final CardSaveService cardSaveService;

    CardSaveIntegrationTest(
            AppUserRepository appUserRepository,
            SourceDocumentRepository sourceDocumentRepository,
            LookupSessionRepository lookupSessionRepository,
            DeckRepository deckRepository,
            CardRepository cardRepository,
            CardSaveService cardSaveService
    ) {
        this.appUserRepository = appUserRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.lookupSessionRepository = lookupSessionRepository;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.cardSaveService = cardSaveService;
    }

    @Test
    void savesTheSelectedSnapshotOnceIntoOneLazyDefaultDeck() {
        Instant now = Instant.now();
        AppUserEntity user = appUserRepository.saveAndFlush(user("card-save-owner"));
        SourceDocumentEntity source = sourceDocumentRepository.save(source(user.getId(), now));
        LookupSessionEntity lookup = lookupSessionRepository.save(lookup(user.getId(), source.getId(), now));
        UserId userId = new UserId(user.getId());
        user.setSourceLanguage("en");
        user.setDefinitionLanguage("es");
        appUserRepository.saveAndFlush(user);

        CardSaveResultDto first = cardSaveService.saveLatest(userId, 2, now);
        CardSaveResultDto repeated = cardSaveService.saveLatest(userId, 2, now.plusMillis(1));

        assertThat(first.outcome()).isEqualTo(CardSaveResultDto.Outcome.SAVED);
        assertThat(repeated.outcome()).isEqualTo(CardSaveResultDto.Outcome.ALREADY_SAVED);
        assertThat(repeated.cardId()).isEqualTo(first.cardId());
        assertThat(repeated.deckId()).isEqualTo(first.deckId());
        assertThat(deckRepository.findByUserIdAndNormalizedName(user.getId(), "default")).isPresent();
        assertThat(cardRepository.findAllByUserIdAndDeckIdOrderByCreatedAt(
                user.getId(), first.deckId().value()
        )).singleElement().satisfies(card -> {
            assertThat(card.getSavedFromLookupSessionId()).isEqualTo(lookup.getId());
            assertThat(card.getSavedFromContextNumber()).isEqualTo(2);
            assertThat(card.getTerm()).isEqualTo("vivir");
            assertThat(card.getDefinition()).isEqualTo("verb: to live");
            assertThat(card.getExampleText()).isEqualTo("Ella vive aquí.");
            assertThat(card.getSourceLanguage()).isEqualTo("es");
            assertThat(card.getDefinitionLanguage()).isEqualTo("en");
            assertThat(card.getSourceId()).isEqualTo(source.getId());
            assertThat(card.getSourceLocation()).containsEntry("pageNumber", 8);
            assertThat(card.getAnkiTemplateVersion()).isEqualTo("whatsanki-context-v1");
            assertThat(card.getPayload()).containsEntry("contextNumber", 2);
            @SuppressWarnings("unchecked")
            Map<String, Object> anki = (Map<String, Object>) card.getPayload().get("anki");
            @SuppressWarnings("unchecked")
            Map<String, String> fields = (Map<String, String>) anki.get("fields");
            assertThat(anki).containsEntry("noteTypeName", "WhatsAnki Context v1");
            assertThat(fields).containsEntry("ContextBefore", "Ella ")
                    .containsEntry("ContextTarget", "vive")
                    .containsEntry("ContextAfter", " aquí.")
                    .containsEntry("SourceLocator", "p. 8");
        });
    }

    @Test
    void neverReadsAnotherUsersLookup() {
        Instant now = Instant.now();
        AppUserEntity owner = appUserRepository.saveAndFlush(user("lookup-owner-for-isolation"));
        AppUserEntity other = appUserRepository.saveAndFlush(user("lookup-non-owner-for-isolation"));
        SourceDocumentEntity source = sourceDocumentRepository.save(source(owner.getId(), now));
        lookupSessionRepository.save(lookup(owner.getId(), source.getId(), now));

        CardSaveResultDto result = cardSaveService.saveLatest(new UserId(other.getId()), 1, now);

        assertThat(result.outcome()).isEqualTo(CardSaveResultDto.Outcome.NO_LOOKUP);
        assertThat(deckRepository.findByUserIdAndNormalizedName(other.getId(), "default")).isEmpty();
    }

    private AppUserEntity user(String identity) {
        AppUserEntity user = new AppUserEntity();
        user.setWhatsappAddressHash(identity.getBytes(StandardCharsets.UTF_8));
        user.setWhatsappAddressCiphertext(("encrypted-" + identity).getBytes(StandardCharsets.UTF_8));
        user.setSourceLanguage("es");
        user.setDefinitionLanguage("en");
        user.setStatus(AppUserEntity.Status.ACTIVE);
        return user;
    }

    private SourceDocumentEntity source(UUID userId, Instant now) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(UUID.randomUUID());
        source.setUserId(userId);
        source.setKind(SourceDocumentEntity.Kind.PDF);
        source.setDisplayName("Book.pdf");
        source.setStatus(SourceDocumentEntity.Status.READY);
        source.setLanguage("es");
        source.setCharacterCount(100L);
        source.setPassageCount(2);
        source.setCreatedAt(now.minusSeconds(120));
        source.setUpdatedAt(now.minusSeconds(120));
        return source;
    }

    private LookupSessionEntity lookup(UUID userId, UUID sourceId, Instant now) {
        DictionaryEntryDto entry = new DictionaryEntryDto(
                "vivir", "vivir", List.of("vive"),
                List.of(new DictionarySenseDto("verb", "to live", "fake:1")),
                new LanguagePairDto("es", "en"),
                new DictionaryProvenanceDto("fake", "v1", "fake:vivir", "", "TEST", "Test data", Map.of())
        );
        LookupSessionEntity lookup = new LookupSessionEntity();
        lookup.setId(UUID.randomUUID());
        lookup.setUserId(userId);
        lookup.setRequestedTerm("vivir");
        lookup.setNormalizedTerm("vivir");
        lookup.setSourceLanguage("es");
        lookup.setDefinitionLanguage("en");
        lookup.setResultPayload(LookupResultPayloadDto.create(entry, List.of(
                context(sourceId, "Quiero vivir aquí.", 7),
                context(sourceId, "Ella vive aquí.", 8)
        )).toMap());
        lookup.setStatus(LookupSessionEntity.Status.READY);
        lookup.setCreatedAt(now.minusSeconds(60));
        lookup.setUpdatedAt(now.minusSeconds(60));
        lookup.setExpiresAt(now.plusSeconds(1_800));
        return lookup;
    }

    private ContextExampleDto context(UUID sourceId, String text, int pageNumber) {
        return new ContextExampleDto(
                UUID.randomUUID(), new SourceId(sourceId), SourceDocumentEntity.Kind.PDF,
                "Book.pdf", new LanguageTag("es"), 0, text, Map.of("pageNumber", pageNumber)
        );
    }
}
