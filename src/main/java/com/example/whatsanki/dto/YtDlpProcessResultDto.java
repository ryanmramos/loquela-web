package com.example.whatsanki.dto;

public record YtDlpProcessResultDto(int exitCode, byte[] stdout, byte[] stderr) { }
