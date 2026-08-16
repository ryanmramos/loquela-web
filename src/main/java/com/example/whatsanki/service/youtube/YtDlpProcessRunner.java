package com.example.whatsanki.service.youtube;

import com.example.whatsanki.dto.YtDlpProcessResultDto;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Deprecated(forRemoval = true)
public interface YtDlpProcessRunner {
    YtDlpProcessResultDto run(List<String> arguments, Path workingDirectory, Duration timeout, int maxStdoutBytes, int maxStderrBytes);
}
