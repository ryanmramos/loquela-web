package com.example.whatsanki.service.twilio;

import com.example.whatsanki.dto.DownloadedMediaDto;

import java.net.URI;

public interface TwilioMediaDownloader {
    DownloadedMediaDto download(URI mediaUri);
}
