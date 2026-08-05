package com.example.whatsanki.service.activity;

import com.example.whatsanki.domain.DeckEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.LookupSessionEntity;
import com.example.whatsanki.domain.OutboundMessageEntity;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.domain.UserIdentityEntity;
import com.example.whatsanki.dto.ActivityItemDto;
import com.example.whatsanki.dto.ActivityPageDto;
import com.example.whatsanki.dto.ActivityStatusQueryResponseDto;
import com.example.whatsanki.repository.ActivityProjectionRepository;
import com.example.whatsanki.repository.DeckRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.LookupSessionRepository;
import com.example.whatsanki.repository.OutboundMessageRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.BrowserActivityService;
import com.example.whatsanki.service.UserIdentityService;
import com.example.whatsanki.service.exception.BrowserAccountUnavailableException;
import com.example.whatsanki.service.exception.BrowserActivityException;
import com.example.whatsanki.service.util.ActivityReferenceCodec;
import com.example.whatsanki.service.util.BrowserReferenceCodec;
import com.example.whatsanki.service.util.SourceReferenceCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrowserActivityServiceImpl implements BrowserActivityService {
    private static final int MAX_LIMIT = 50;

    private final UserIdentityService userIdentityService;
    private final ActivityProjectionRepository activityProjectionRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final LookupSessionRepository lookupSessionRepository;
    private final OutboundMessageRepository outboundMessageRepository;
    private final ExportRequestRepository exportRequestRepository;
    private final DeckRepository deckRepository;

    @Override
    @Transactional(readOnly = true)
    public ActivityPageDto list(
            String googleSubject,
            ActivityItemDto.Category category,
            ActivityItemDto.Lifecycle lifecycle,
            String cursor,
            int limit
    ) {
        return listByUser(resolveUser(googleSubject), category, lifecycle, cursor, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageDto listByUser(
            UserId userId,
            ActivityItemDto.Category category,
            ActivityItemDto.Lifecycle lifecycle,
            String cursor,
            int limit
    ) {
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        ActivityReferenceCodec.Cursor decodedCursor = null;
        if (cursor != null) {
            decodedCursor = ActivityReferenceCodec.decodeCursor(cursor)
                    .orElseThrow(() -> new BrowserActivityException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVITY_CURSOR"));
        }
        List<ActivityProjectionRepository.ActivityRow> rows = activityProjectionRepository.findPage(
                userId.value(),
                category == null ? null : category.name(),
                lifecycle == null ? null : lifecycle.name(),
                decodedCursor == null ? null : decodedCursor.updatedAt(),
                decodedCursor == null ? null : decodedCursor.category().name(),
                decodedCursor == null ? null : decodedCursor.id(),
                boundedLimit + 1
        );
        boolean hasNext = rows.size() > boundedLimit;
        List<ActivityProjectionRepository.ActivityRow> visibleRows = hasNext
                ? rows.subList(0, boundedLimit)
                : rows;
        List<ActivityItemDto> items = visibleRows.stream().map(this::fromProjection).toList();
        String nextCursor = null;
        if (hasNext && !visibleRows.isEmpty()) {
            ActivityProjectionRepository.ActivityRow last = visibleRows.get(visibleRows.size() - 1);
            nextCursor = ActivityReferenceCodec.encodeCursor(
                    last.getUpdatedAt(), ActivityItemDto.Category.valueOf(last.getCategory()), last.getId()
            );
        }
        return new ActivityPageDto(items, nextCursor, activityProjectionRepository.countActive(userId.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityStatusQueryResponseDto queryStatuses(String googleSubject, List<String> references) {
        UserId userId = resolveUser(googleSubject);
        Map<ActivityItemDto.Category, Set<UUID>> ids = references.stream()
                .map(ActivityReferenceCodec::decode)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.groupingBy(
                        ActivityReferenceCodec.Reference::category,
                        () -> new EnumMap<>(ActivityItemDto.Category.class),
                        Collectors.mapping(ActivityReferenceCodec.Reference::id, Collectors.toSet())
                ));

        Map<String, ActivityItemDto> found = new HashMap<>();
        mapSources(userId, ids.getOrDefault(ActivityItemDto.Category.SOURCE, Set.of()), found);
        mapLookups(userId, ids.getOrDefault(ActivityItemDto.Category.LOOKUP, Set.of()), found);
        mapExports(userId, ids.getOrDefault(ActivityItemDto.Category.EXPORT, Set.of()), found);

        // Preserve request order and silently omit malformed, missing, or cross-user references.
        List<ActivityItemDto> ordered = references.stream().distinct().map(found::get).filter(java.util.Objects::nonNull).toList();
        return new ActivityStatusQueryResponseDto(ordered, activityProjectionRepository.countActive(userId.value()));
    }

    private void mapSources(UserId userId, Set<UUID> ids, Map<String, ActivityItemDto> found) {
        if (ids.isEmpty()) return;
        sourceDocumentRepository.findAllByUserIdAndIdIn(userId.value(), ids).stream()
                .map(this::fromSource)
                .forEach(item -> found.put(item.reference(), item));
    }

    private void mapLookups(UserId userId, Set<UUID> ids, Map<String, ActivityItemDto> found) {
        if (ids.isEmpty()) return;
        Map<UUID, List<OutboundMessageEntity>> outbound = outboundMessageRepository
                .findAllByUserIdAndLookupSessionIdIn(userId.value(), ids)
                .stream()
                .collect(Collectors.groupingBy(OutboundMessageEntity::getLookupSessionId));
        lookupSessionRepository.findAllByUserIdAndIdIn(userId.value(), ids).stream()
                .map(lookup -> fromLookup(lookup, outbound.getOrDefault(lookup.getId(), List.of())))
                .forEach(item -> found.put(item.reference(), item));
    }

    private void mapExports(UserId userId, Set<UUID> ids, Map<String, ActivityItemDto> found) {
        if (ids.isEmpty()) return;
        List<ExportRequestEntity> exports = exportRequestRepository.findAllByUserIdAndIdIn(userId.value(), ids);
        Set<UUID> deckIds = exports.stream().map(ExportRequestEntity::getDeckId).collect(Collectors.toSet());
        Map<UUID, String> deckNames = deckRepository.findAllByUserIdAndIdIn(userId.value(), deckIds).stream()
                .collect(Collectors.toMap(DeckEntity::getId, DeckEntity::getName));
        exports.stream()
                .map(export -> fromExport(export, deckNames.get(export.getDeckId())))
                .forEach(item -> found.put(item.reference(), item));
    }

    private ActivityItemDto fromProjection(ActivityProjectionRepository.ActivityRow row) {
        ActivityItemDto.Category category = ActivityItemDto.Category.valueOf(row.getCategory());
        return new ActivityItemDto(
                ActivityReferenceCodec.encode(category, row.getId()),
                category,
                ActivityItemDto.Operation.valueOf(row.getOperation()),
                ActivityItemDto.Lifecycle.valueOf(row.getLifecycle()),
                ActivityItemDto.Status.valueOf(row.getStatus()),
                row.getResourceLabel(),
                row.getDeliveryStatus() == null
                        ? null
                        : ActivityItemDto.DeliveryStatus.valueOf(row.getDeliveryStatus()),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                target(category, row.getId())
        );
    }

    private ActivityItemDto fromSource(SourceDocumentEntity source) {
        ActivityItemDto.Operation operation = source.getStatus() == SourceDocumentEntity.Status.DELETING
                || source.getStatus() == SourceDocumentEntity.Status.DELETED
                ? ActivityItemDto.Operation.DELETE_SOURCE
                : ActivityItemDto.Operation.INGEST_SOURCE;
        ActivityItemDto.Lifecycle lifecycle = switch (source.getStatus()) {
            case FAILED -> ActivityItemDto.Lifecycle.FAILED;
            case READY, DELETED -> ActivityItemDto.Lifecycle.COMPLETED;
            default -> ActivityItemDto.Lifecycle.ACTIVE;
        };
        return item(source.getId(), ActivityItemDto.Category.SOURCE, operation, lifecycle,
                ActivityItemDto.Status.valueOf(source.getStatus().name()), source.getDisplayName(), null,
                source.getCreatedAt(), source.getUpdatedAt());
    }

    private ActivityItemDto fromLookup(LookupSessionEntity lookup, List<OutboundMessageEntity> outbound) {
        ActivityItemDto.DeliveryStatus delivery = deliveryStatus(lookup, outbound);
        ActivityItemDto.Lifecycle lifecycle;
        if (lookup.getStatus() == LookupSessionEntity.Status.FAILED
                || delivery == ActivityItemDto.DeliveryStatus.FAILED) {
            lifecycle = ActivityItemDto.Lifecycle.FAILED;
        } else if (lookup.getStatus() == LookupSessionEntity.Status.EXPIRED
                || (lookup.getStatus() == LookupSessionEntity.Status.READY
                && (delivery == ActivityItemDto.DeliveryStatus.NOT_APPLICABLE
                || delivery == ActivityItemDto.DeliveryStatus.PROVIDER_ACCEPTED))) {
            lifecycle = ActivityItemDto.Lifecycle.COMPLETED;
        } else {
            lifecycle = ActivityItemDto.Lifecycle.ACTIVE;
        }
        Instant updatedAt = outbound.stream().map(OutboundMessageEntity::getUpdatedAt)
                .max(Comparator.naturalOrder()).filter(value -> value.isAfter(lookup.getUpdatedAt()))
                .orElse(lookup.getUpdatedAt());
        return item(lookup.getId(), ActivityItemDto.Category.LOOKUP, ActivityItemDto.Operation.LOOKUP,
                lifecycle, ActivityItemDto.Status.valueOf(lookup.getStatus().name()), lookup.getRequestedTerm(), delivery,
                lookup.getCreatedAt(), updatedAt);
    }

    private ActivityItemDto fromExport(ExportRequestEntity export, String deckName) {
        ActivityItemDto.Lifecycle lifecycle = switch (export.getState()) {
            case FAILED -> ActivityItemDto.Lifecycle.FAILED;
            case READY, EXPIRED -> ActivityItemDto.Lifecycle.COMPLETED;
            default -> ActivityItemDto.Lifecycle.ACTIVE;
        };
        return item(export.getId(), ActivityItemDto.Category.EXPORT, ActivityItemDto.Operation.EXPORT_DECK,
                lifecycle, ActivityItemDto.Status.valueOf(export.getState().name()), deckName, null, export.getCreatedAt(), export.getUpdatedAt());
    }

    private ActivityItemDto item(
            UUID id,
            ActivityItemDto.Category category,
            ActivityItemDto.Operation operation,
            ActivityItemDto.Lifecycle lifecycle,
            ActivityItemDto.Status status,
            String resourceLabel,
            ActivityItemDto.DeliveryStatus deliveryStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ActivityItemDto(
                ActivityReferenceCodec.encode(category, id), category, operation, lifecycle, status,
                resourceLabel, deliveryStatus, createdAt, updatedAt, target(category, id)
        );
    }

    private ActivityItemDto.DeliveryStatus deliveryStatus(
            LookupSessionEntity lookup,
            List<OutboundMessageEntity> outbound
    ) {
        if (lookup.getInboundMessageId() == null) return ActivityItemDto.DeliveryStatus.NOT_APPLICABLE;
        if (outbound.stream().anyMatch(message -> message.getStatus() == OutboundMessageEntity.Status.FAILED)) {
            return ActivityItemDto.DeliveryStatus.FAILED;
        }
        if (!outbound.isEmpty() && outbound.stream()
                .allMatch(message -> message.getStatus() == OutboundMessageEntity.Status.PROVIDER_ACCEPTED)) {
            return ActivityItemDto.DeliveryStatus.PROVIDER_ACCEPTED;
        }
        return ActivityItemDto.DeliveryStatus.PENDING;
    }

    private String target(ActivityItemDto.Category category, UUID id) {
        return switch (category) {
            case SOURCE -> "/app/sources/" + SourceReferenceCodec.encode(new SourceId(id));
            case LOOKUP -> "/app/lookup?reference=" + BrowserReferenceCodec.encode(id);
            case EXPORT -> "/app/decks?exportReference=" + BrowserReferenceCodec.encode(id);
        };
    }

    private UserId resolveUser(String googleSubject) {
        return userIdentityService.findUserId(UserIdentityEntity.Provider.GOOGLE, googleSubject)
                .orElseThrow(() -> new BrowserAccountUnavailableException("ACCOUNT_DELETED"));
    }
}
