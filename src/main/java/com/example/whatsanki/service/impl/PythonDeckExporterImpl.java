package com.example.whatsanki.service.impl;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.ExportedDeckDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.DeckExporter;
import com.example.whatsanki.service.exception.DeckExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** CLI transport for the isolated Python/genanki worker. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PythonDeckExporterImpl implements DeckExporter {

    private final ObjectMapper objectMapper;
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public ExportedDeckDto export(Map<String, Object> snapshot) {
        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("whatsanki-export-");
            Path input = temporaryDirectory.resolve("snapshot.json");
            Path output = temporaryDirectory.resolve("deck.apkg");
            Path stdout = temporaryDirectory.resolve("result.json");
            Path stderr = temporaryDirectory.resolve("worker.log");
            objectMapper.writeValue(input.toFile(), snapshot);

            WhatsAnkiConfigurationDto.Exporter exporter = configuration.getExporter();
            Path workingDirectory = exporter.getWorkingDirectory().toAbsolutePath().normalize();
            Path sourceDirectory = workingDirectory.resolve("src");
            if (!Files.isDirectory(sourceDirectory)) {
                throw DeckExportException.permanent("EXPORTER_CONFIGURATION_INVALID");
            }
            ProcessBuilder builder = new ProcessBuilder(List.of(
                    exporter.getPythonExecutable(), "-m", exporter.getModule(),
                    input.toAbsolutePath().toString(), output.toAbsolutePath().toString()
            ));
            builder.directory(workingDirectory.toFile());
            // The source tree is used in local/container deployments without requiring
            // the Spring process to mutate its environment by installing a wheel.
            builder.environment().put("PYTHONPATH", sourceDirectory.toString());
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());

            Process process = builder.start();
            Duration timeout = exporter.getTimeout();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw DeckExportException.transientFailure("EXPORTER_TIMEOUT", null);
            }

            Map<?, ?> result = readResult(stdout);
            if (process.exitValue() != 0 || !"ok".equals(result.get("status"))) {
                String errorCode = safeErrorCode(result.get("errorCode"));
                throw new DeckExportException(
                        retryable(errorCode) ? BackgroundJobFailureKind.TRANSIENT : BackgroundJobFailureKind.PERMANENT,
                        errorCode,
                        null
                );
            }
            ExportedDeckDto artifact = verifyArtifact(output, result, exporter.getMaxArtifactBytes());
            log.info("Python exporter produced verified artifact byteCount={} sha256Prefix={}",
                    artifact.byteSize(), HexFormat.of().formatHex(artifact.sha256()).substring(0, 12));
            deleteQuietly(input);
            deleteQuietly(stdout);
            deleteQuietly(stderr);
            return artifact;
        } catch (DeckExportException exception) {
            cleanupDirectory(temporaryDirectory);
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cleanupDirectory(temporaryDirectory);
            throw DeckExportException.transientFailure("EXPORTER_INTERRUPTED", exception);
        } catch (IOException | RuntimeException exception) {
            cleanupDirectory(temporaryDirectory);
            throw DeckExportException.transientFailure("EXPORTER_PROCESS_FAILED", exception);
        }
    }

    private Map<?, ?> readResult(Path stdout) throws IOException {
        if (!Files.isRegularFile(stdout) || Files.size(stdout) == 0) {
            return Map.of("status", "error", "errorCode", "EXPORTER_INVALID_RESPONSE");
        }
        return objectMapper.readValue(stdout.toFile(), Map.class);
    }

    private ExportedDeckDto verifyArtifact(Path output, Map<?, ?> result, long maxBytes) throws IOException {
        if (!Files.isRegularFile(output)) {
            throw DeckExportException.transientFailure("EXPORTER_ARTIFACT_MISSING", null);
        }
        long size = Files.size(output);
        if (size < 1 || size > maxBytes || !(result.get("byteSize") instanceof Number resultSize)
                || resultSize.longValue() != size) {
            throw DeckExportException.permanent("EXPORTER_ARTIFACT_INVALID");
        }
        byte[] digest = sha256(Files.readAllBytes(output));
        String workerDigest = result.get("sha256") instanceof String value ? value : "";
        if (!HexFormat.of().formatHex(digest).equals(workerDigest)) {
            throw DeckExportException.permanent("EXPORTER_CHECKSUM_MISMATCH");
        }
        return new ExportedDeckDto(output, size, digest);
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safeErrorCode(Object value) {
        if (value instanceof String code && code.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return code;
        }
        return "EXPORTER_INVALID_RESPONSE";
    }

    private boolean retryable(String code) {
        return code.equals("EXPORTER_INTERNAL_ERROR") || code.equals("EXPORTER_WRITE_FAILED");
    }

    private void cleanupDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.list(directory)) {
            paths.forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // Best-effort cleanup must not replace the safe exporter error.
        }
        deleteQuietly(directory);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            log.warn("Could not remove temporary exporter path fileName={}", path.getFileName());
        }
    }
}
