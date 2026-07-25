package com.example.whatsanki.repository;

import com.example.whatsanki.domain.VideoSourceEntity;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface VideoSourceRepository extends Repository<VideoSourceEntity, UUID> {
    VideoSourceEntity save(VideoSourceEntity source);
    Optional<VideoSourceEntity> findBySourceIdAndUserId(UUID sourceId, UUID userId);
    Optional<VideoSourceEntity> findByUserIdAndProviderAndProviderVideoIdAndCaptionLanguage(
            UUID userId,
            VideoSourceEntity.Provider provider,
            String providerVideoId,
            String captionLanguage
    );
}
