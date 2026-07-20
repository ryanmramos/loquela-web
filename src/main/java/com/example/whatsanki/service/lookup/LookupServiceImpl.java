package com.example.whatsanki.service.lookup;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.InboundMessageId;
import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.LookupSessionId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.DictionaryEntryDto;
import com.example.whatsanki.dto.DictionaryLookupRequestDto;
import com.example.whatsanki.dto.LanguagePairDto;
import com.example.whatsanki.dto.LookupAcceptanceDto;
import com.example.whatsanki.dto.LookupExecutionPreparationDto;
import com.example.whatsanki.dto.LookupExecutionResultDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.UserLanguageSettingsService;
import com.example.whatsanki.service.dictionary.DictionaryProvider;
import com.example.whatsanki.service.dictionary.DictionaryProviderSelector;
import com.example.whatsanki.service.exception.DictionaryProviderException;
import com.example.whatsanki.service.exception.InvalidLookupTermException;
import com.example.whatsanki.service.exception.LookupExecutionException;
import com.example.whatsanki.service.exception.UnsupportedDictionaryLanguagePairException;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupServiceImpl implements LookupService {

    private final UserLanguageSettingsService userLanguageSettingsService;
    private final LookupSessionLifecycleService lifecycleService;
    private final DictionaryProviderSelector dictionaryProviderSelector;
    private final CorpusSearch corpusSearch;
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public LookupAcceptanceDto accept(
            UserId userId,
            InboundMessageId inboundMessageId,
            String requestedTerm
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(inboundMessageId, "inboundMessageId must not be null");
        String displayTerm = validateDisplayTerm(requestedTerm);
        String normalizedTerm = textNormalizer.normalizeForSearch(displayTerm);
        validateTermLength(normalizedTerm);

        // Resolve once before creating the durable row. LANGUAGE changes after this point
        // cannot alter either provider direction or corpus language for the queued job.
        LanguagePairDto languagePair = userLanguageSettingsService.getCurrent(userId);
        return lifecycleService.accept(
                userId,
                inboundMessageId,
                displayTerm,
                normalizedTerm,
                languagePair,
                Instant.now()
        );
    }

    @Override
    public LookupExecutionResultDto execute(UserId userId, LookupSessionId lookupSessionId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(lookupSessionId, "lookupSessionId must not be null");
        Instant startedAt = Instant.now();
        LookupExecutionPreparationDto preparation = lifecycleService.prepare(
                userId, lookupSessionId, startedAt
        );
        if (preparation.alreadyCompleted()) {
            log.info(
                    "Skipped completed lookup job userId={} lookupSessionId={}",
                    userId.value(), lookupSessionId.value()
            );
            return new LookupExecutionResultDto(
                    lookupSessionId, LookupExecutionResultDto.Outcome.ALREADY_COMPLETED, 0
            );
        }
        if (preparation.expired()) {
            lifecycleService.markExpired(userId, lookupSessionId, startedAt);
            return new LookupExecutionResultDto(
                    lookupSessionId, LookupExecutionResultDto.Outcome.EXPIRED, 0
            );
        }

        // This orchestration method deliberately has no transaction. Provider I/O and
        // corpus ranking happen between short lifecycle transactions managed by the
        // lifecycle service, so a slow external dictionary never holds database locks.
        DictionaryProvider provider = dictionaryProviderSelector.activeProvider();
        DictionaryEntryDto entry;
        try {
            entry = provider.lookup(new DictionaryLookupRequestDto(
                    preparation.requestedTerm(), preparation.languagePair()
            )).orElseThrow(() -> LookupExecutionException.permanent("DEFINITION_NOT_FOUND"));
        } catch (UnsupportedDictionaryLanguagePairException exception) {
            throw new LookupExecutionException(
                    BackgroundJobFailureKind.PERMANENT,
                    "UNSUPPORTED_DICTIONARY_PAIR",
                    exception
            );
        } catch (DictionaryProviderException exception) {
            LookupExecutionException classified = classifyProviderFailure(exception);
            log.warn(
                    "Dictionary provider lookup failed providerId={} providerFailureKind={} "
                            + "jobFailureKind={} errorCode={} sourceLanguage={} definitionLanguage={}",
                    exception.providerId(),
                    exception.failureKind(),
                    classified.failureKind(),
                    classified.errorCode(),
                    preparation.languagePair().sourceLanguage(),
                    preparation.languagePair().definitionLanguage()
            );
            throw classified;
        }
        if (!entry.languagePair().equals(preparation.languagePair())) {
            throw LookupExecutionException.permanent("PROVIDER_LANGUAGE_MISMATCH");
        }

        List<ContextExampleDto> contexts = corpusSearch.findExamples(
                userId,
                entry.searchTerms(),
                new CorpusSearchOptionsDto(
                        new LanguageTag(preparation.languagePair().sourceLanguage()),
                        configuration.getSearch().getLookupResults(),
                        configuration.getSearch().getLookupMaxResultsPerSource()
                )
        );
        if (contexts.isEmpty()) {
            throw LookupExecutionException.permanent("CONTEXT_NOT_FOUND");
        }

        boolean completed = lifecycleService.complete(
                userId, lookupSessionId, entry, contexts, Instant.now()
        );
        LookupExecutionResultDto.Outcome outcome = completed
                ? LookupExecutionResultDto.Outcome.COMPLETED
                : LookupExecutionResultDto.Outcome.EXPIRED;
        log.info(
                "Executed lookup userId={} lookupSessionId={} providerId={} sourceLanguage={} "
                        + "definitionLanguage={} contextCount={} outcome={}",
                userId.value(),
                lookupSessionId.value(),
                provider.providerId(),
                preparation.languagePair().sourceLanguage(),
                preparation.languagePair().definitionLanguage(),
                completed ? contexts.size() : 0,
                outcome
        );
        return new LookupExecutionResultDto(
                lookupSessionId, outcome, completed ? contexts.size() : 0
        );
    }

    @Override
    public void markFailed(UserId userId, LookupSessionId lookupSessionId, String errorCode) {
        lifecycleService.markFailed(userId, lookupSessionId, errorCode, Instant.now());
    }

    private LookupExecutionException classifyProviderFailure(DictionaryProviderException exception) {
        // Provider adapters expose semantic categories rather than HTTP/client details.
        // Stable codes keep retry policy and monitoring independent of the chosen vendor.
        return switch (exception.failureKind()) {
            case TIMEOUT -> new LookupExecutionException(
                    BackgroundJobFailureKind.TRANSIENT, "PROVIDER_TIMEOUT", exception
            );
            case RATE_LIMITED -> new LookupExecutionException(
                    BackgroundJobFailureKind.TRANSIENT, "PROVIDER_RATE_LIMITED", exception
            );
            case TEMPORARY -> new LookupExecutionException(
                    BackgroundJobFailureKind.TRANSIENT, "PROVIDER_TEMPORARY_FAILURE", exception
            );
            case PERMANENT -> new LookupExecutionException(
                    BackgroundJobFailureKind.PERMANENT, "PROVIDER_REQUEST_REJECTED", exception
            );
        };
    }

    private String validateDisplayTerm(String requestedTerm) {
        if (requestedTerm == null || requestedTerm.isBlank()) {
            throw new InvalidLookupTermException("Lookup term must not be blank");
        }
        String stripped = requestedTerm.strip();
        validateTermLength(stripped);
        return stripped;
    }

    private void validateTermLength(String term) {
        int length = term.codePointCount(0, term.length());
        if (term.isBlank() || length > configuration.getSearch().getMaxTermCharacters()) {
            throw new InvalidLookupTermException(
                    "Lookup term must contain between 1 and "
                            + configuration.getSearch().getMaxTermCharacters() + " characters"
            );
        }
    }
}
