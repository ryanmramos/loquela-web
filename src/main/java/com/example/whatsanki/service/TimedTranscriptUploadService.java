package com.example.whatsanki.service;

import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.CreateTimedTranscriptUploadRequestDto;
import com.example.whatsanki.dto.SourceUploadIntentDto;
import org.springframework.web.multipart.MultipartFile;

public interface TimedTranscriptUploadService {
    SourceUploadIntentDto createIntent(String googleSubject, CreateTimedTranscriptUploadRequestDto request);
    AcceptedSourceDto complete(String googleSubject, String uploadReference, MultipartFile file);
}
