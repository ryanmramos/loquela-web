package com.example.whatsanki.service.youtube;

import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.YtDlpProcessResultDto;
import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import com.example.whatsanki.service.exception.YouTubeSubtitleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class YtDlpProcessRunnerImpl implements YtDlpProcessRunner {
    private final WhatsAnkiConfigurationDto configuration;

    @Override
    public YtDlpProcessResultDto run(
            List<String> arguments,
            Path workingDirectory,
            Duration timeout,
            int maxStdoutBytes,
            int maxStderrBytes
    ) {
        List<String> command = new ArrayList<>();
        command.add(configuration.getYoutube().getExecutable());
        command.addAll(arguments);
        Process process;
        try {
            // ProcessBuilder receives a fixed argument array. No shell interprets URL,
            // language, paths, or output text as executable syntax.
            process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        } catch (IOException exception) {
            throw new YouTubeSubtitleException(BackgroundJobFailureKind.TRANSIENT, "YTDLP_START_FAILED", exception);
        }

        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(
                () -> boundedRead(process.getInputStream(), maxStdoutBytes, process)
        );
        CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(
                () -> boundedRead(process.getErrorStream(), maxStderrBytes, process)
        );
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new YouTubeSubtitleException(BackgroundJobFailureKind.TRANSIENT, "YTDLP_TIMEOUT");
            }
            return new YtDlpProcessResultDto(process.exitValue(), stdout.join(), stderr.join());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new YouTubeSubtitleException(BackgroundJobFailureKind.TRANSIENT, "YTDLP_INTERRUPTED", exception);
        } catch (RuntimeException exception) {
            process.destroyForcibly();
            if (exception instanceof YouTubeSubtitleException typed) throw typed;
            if (exception instanceof CompletionException completion
                    && completion.getCause() instanceof YouTubeSubtitleException typed) {
                throw typed;
            }
            throw new YouTubeSubtitleException(BackgroundJobFailureKind.TRANSIENT, "YTDLP_IO_FAILED", exception);
        }
    }

    private byte[] boundedRead(InputStream stream, int maximum, Process process) {
        try (stream; ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = stream.read(buffer)) >= 0; ) {
                total += read;
                if (total > maximum) {
                    process.destroyForcibly();
                    throw new YouTubeSubtitleException(BackgroundJobFailureKind.PERMANENT, "YTDLP_OUTPUT_TOO_LARGE");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new YouTubeSubtitleException(BackgroundJobFailureKind.TRANSIENT, "YTDLP_IO_FAILED", exception);
        }
    }
}
