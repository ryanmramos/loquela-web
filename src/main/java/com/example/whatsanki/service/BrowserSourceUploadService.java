package com.example.whatsanki.service;

import com.example.whatsanki.dto.AcceptedSourceDto;
import com.example.whatsanki.dto.CreateSourceUploadRequestDto;
import com.example.whatsanki.dto.SourceUploadIntentDto;
import org.springframework.web.multipart.MultipartFile;

public interface BrowserSourceUploadService {
    SourceUploadIntentDto createIntent(String googleSubject, CreateSourceUploadRequestDto request);

    AcceptedSourceDto complete(String googleSubject, String uploadReference, MultipartFile file);
}
