package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.LanguageTag;
import com.example.whatsanki.domain.PassageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ContextExampleDto;
import com.example.whatsanki.dto.CorpusSearchOptionsDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.repository.PassageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.CorpusSearch;
import com.example.whatsanki.service.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorpusSearchImpl implements CorpusSearch {

    private final PassageRepository passageRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final TextNormalizer textNormalizer;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    @Transactional(readOnly = true)
    public List<ContextExampleDto> findExamples(
            UserId userId,
            String term,
            CorpusSearchOptionsDto options
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(options, "options must not be null");
        validateOptions(options);

        String normalizedTerm = normalizeTerm(term);
        long requestedCandidates = (long) options.limit()
                * configuration.getSearch().getCandidateMultiplier();
        int candidateLimit = (int) Math.min(
                configuration.getSearch().getMaxCandidates(),
                Math.max((long) options.limit(), requestedCandidates)
        );
        List<PassageEntity> rankedCandidates = passageRepository.searchRankedReadyPassages(
                userId.value(),
                options.sourceLanguage().value(),
                normalizedTerm,
                configuration.getSearch().getIdealPassageCharacters(),
                PageRequest.of(0, candidateLimit)
        );

        if (rankedCandidates.isEmpty()) {
            log.info(
                    "Corpus search completed userId={} sourceLanguage={} termCharacters={} candidateCount=0 resultCount=0",
                    userId.value(), options.sourceLanguage().value(), codePointLength(normalizedTerm)
            );
            return List.of();
        }

        Map<UUID, SourceDocumentEntity> sources = sourceDocumentRepository
                .findAllByUserIdAndIdIn(
                        userId.value(),
                        rankedCandidates.stream().map(PassageEntity::getSourceId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(SourceDocumentEntity::getId, Function.identity()));
        List<PassageEntity> diversified = diversify(
                rankedCandidates,
                options.limit(),
                options.maxResultsPerSource()
        );
        List<ContextExampleDto> results = diversified.stream()
                .map(passage -> toContextExample(passage, sources.get(passage.getSourceId())))
                .filter(Objects::nonNull)
                .toList();

        log.info(
                "Corpus search completed userId={} sourceLanguage={} termCharacters={} candidateCount={} resultCount={}",
                userId.value(),
                options.sourceLanguage().value(),
                codePointLength(normalizedTerm),
                rankedCandidates.size(),
                results.size()
        );
        return results;
    }

    private String normalizeTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("term must not be blank");
        }
        String normalized = textNormalizer.normalizeForSearch(term);
        int length = codePointLength(normalized);
        if (normalized.isBlank() || length > configuration.getSearch().getMaxTermCharacters()) {
            throw new IllegalArgumentException(
                    "term must contain between 1 and "
                            + configuration.getSearch().getMaxTermCharacters()
                            + " characters"
            );
        }
        return normalized;
    }

    private void validateOptions(CorpusSearchOptionsDto options) {
        if (options.limit() > configuration.getSearch().getMaxResults()) {
            throw new IllegalArgumentException(
                    "limit must not exceed " + configuration.getSearch().getMaxResults()
            );
        }
        if (options.maxResultsPerSource() > options.limit()) {
            throw new IllegalArgumentException("maxResultsPerSource must not exceed limit");
        }
    }

    private List<PassageEntity> diversify(
            List<PassageEntity> rankedCandidates,
            int limit,
            int maxResultsPerSource
    ) {
        Map<UUID, ArrayDeque<PassageEntity>> candidatesBySource = new LinkedHashMap<>();
        rankedCandidates.forEach(candidate -> candidatesBySource
                .computeIfAbsent(candidate.getSourceId(), ignored -> new ArrayDeque<>())
                .add(candidate));

        // Round-robin selection prevents one long source from consuming every context
        // slot while preserving the repository's rank order within each source.
        List<PassageEntity> selected = new ArrayList<>(limit);
        Map<UUID, Integer> selectedBySource = new LinkedHashMap<>();
        boolean selectedInRound;
        do {
            selectedInRound = false;
            for (Map.Entry<UUID, ArrayDeque<PassageEntity>> entry : candidatesBySource.entrySet()) {
                if (selected.size() >= limit) {
                    break;
                }
                int selectedCount = selectedBySource.getOrDefault(entry.getKey(), 0);
                if (selectedCount >= maxResultsPerSource || entry.getValue().isEmpty()) {
                    continue;
                }
                selected.add(entry.getValue().removeFirst());
                selectedBySource.put(entry.getKey(), selectedCount + 1);
                selectedInRound = true;
            }
        } while (selectedInRound && selected.size() < limit);
        return List.copyOf(selected);
    }

    private ContextExampleDto toContextExample(PassageEntity passage, SourceDocumentEntity source) {
        if (source == null || source.getLanguage() == null) {
            return null;
        }
        return new ContextExampleDto(
                passage.getId(),
                new SourceId(passage.getSourceId()),
                source.getDisplayName(),
                new LanguageTag(source.getLanguage()),
                passage.getOrdinal(),
                passage.getDisplayText(),
                passage.getLocation()
        );
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
