package com.example.whatsanki.dto;

import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;

public record YouTubeSourceAcceptanceDto(SourceId sourceId, JobId jobId, boolean existing) { }
