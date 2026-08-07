package com.example.whatsanki.service.exception;

public class YouTubeIngestionDisabledException extends RuntimeException {
    public YouTubeIngestionDisabledException() {
        super("YOUTUBE_INGESTION_DISABLED");
    }
}
