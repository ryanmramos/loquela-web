package com.example.whatsanki.service;

import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;

public interface SourceDeletionService {
    boolean delete(UserId userId, SourceId sourceId, JobId deletionJobId);
}
