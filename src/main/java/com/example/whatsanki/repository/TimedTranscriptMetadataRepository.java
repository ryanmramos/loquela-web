package com.example.whatsanki.repository;

import com.example.whatsanki.domain.TimedTranscriptMetadataEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface TimedTranscriptMetadataRepository extends Repository<TimedTranscriptMetadataEntity, UUID> {
    TimedTranscriptMetadataEntity save(TimedTranscriptMetadataEntity metadata);
    Optional<TimedTranscriptMetadataEntity> findBySourceIdAndUserId(UUID sourceId, UUID userId);
    List<TimedTranscriptMetadataEntity> findAllByTemporaryObjectKeyIsNotNull();
}
