package com.example.whatsanki.service.demo.impl;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
class InMemoryDemoWorkspaceStore implements DemoWorkspaceStore {
    private final WhatsAnkiConfigurationDto configuration;
    private final Map<UUID, DemoWorkspace> workspaces = new ConcurrentHashMap<>();

    @Override
    public synchronized DemoWorkspace create(Instant now) {
        while (workspaces.size() >= configuration.getDemo().getMaxWorkspaces()) {
            workspaces.values().stream()
                    .min(Comparator.comparing(workspace -> workspace.lastAccessedAt))
                    .ifPresent(workspace -> remove(workspace.id));
        }
        DemoWorkspace workspace = new DemoWorkspace(
                now,
                configuration.getLanguages().getDefaultSource(),
                configuration.getLanguages().getDefaultDefinition()
        );
        workspaces.put(workspace.id, workspace);
        log.info("Started anonymous demo workspace workspaceId={}", workspace.id);
        return workspace;
    }

    @Override
    public DemoWorkspace resolve(UUID reference, Instant now) {
        DemoWorkspace workspace = workspaces.get(reference);
        if (workspace == null) return null;
        synchronized (workspace) {
            if (expired(workspace, now)) {
                remove(reference);
                return null;
            }
            workspace.lastAccessedAt = now;
            return workspace;
        }
    }

    @Override
    public DemoWorkspace reset(UUID reference, Instant now) {
        remove(reference);
        return create(now);
    }

    @Override
    public void remove(UUID reference) {
        DemoWorkspace workspace = workspaces.remove(reference);
        if (workspace != null) cleanup(workspace);
    }

    @Scheduled(fixedDelay = 60_000)
    void removeExpired() {
        Instant now = Instant.now();
        workspaces.forEach((id, workspace) -> {
            synchronized (workspace) {
                if (expired(workspace, now)) {
                    remove(id);
                } else {
                    workspace.sources.replaceAll((type, source) -> expireRetryInput(source, now));
                }
            }
        });
    }

    private DemoWorkspace.Source expireRetryInput(DemoWorkspace.Source source, Instant now) {
        if (source.retryInput() == null || source.retryExpiresAt() == null
                || now.isBefore(source.retryExpiresAt())) {
            return source;
        }
        if (source.retryInput() instanceof Path path) delete(path);
        return new DemoWorkspace.Source(source.reference(), source.revision(), source.type(), source.label(),
                source.state(), source.safeErrorCode(), source.characterCount(), source.passages(), null, null);
    }

    @PreDestroy
    void shutdown() {
        workspaces.keySet().forEach(this::remove);
    }

    private boolean expired(DemoWorkspace workspace, Instant now) {
        return !now.isBefore(workspace.createdAt.plus(configuration.getDemo().getAbsoluteLifetime()))
                || !now.isBefore(workspace.lastAccessedAt.plus(configuration.getDemo().getInactivityTimeout()));
    }

    private void cleanup(DemoWorkspace workspace) {
        synchronized (workspace) {
            workspace.tasks.values().forEach(task -> task.cancel(true));
            workspace.sources.values().forEach(source -> {
                if (source.retryInput() instanceof Path path) delete(path);
            });
            if (workspace.cachedExport != null) deleteTree(workspace.cachedExport.path().getParent());
            workspace.tasks.clear();
            workspace.sources.clear();
            workspace.lookupCache.clear();
            workspace.cards.clear();
            workspace.tombstones.clear();
        }
        log.info("Cleaned anonymous demo workspace workspaceId={}", workspace.id);
    }

    private void deleteTree(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::delete);
        } catch (IOException ignored) {
            log.warn("Could not fully clean an anonymous demo artifact directory");
        }
    }

    private void delete(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { log.warn("Could not clean an anonymous demo temporary file"); }
    }
}
