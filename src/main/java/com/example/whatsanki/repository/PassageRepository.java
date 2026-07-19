package com.example.whatsanki.repository;

import com.example.whatsanki.domain.PassageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PassageRepository extends Repository<PassageEntity, UUID> {
    PassageEntity save(PassageEntity passage);
    List<PassageEntity> saveAll(Iterable<PassageEntity> passages);
    long deleteAllByUserIdAndSourceId(UUID userId, UUID sourceId);
    Optional<PassageEntity> findByIdAndUserId(UUID id, UUID userId);
    List<PassageEntity> findAllByUserIdAndSourceIdOrderByOrdinal(UUID userId, UUID sourceId);

    @Query(value = """
            select p.*
            from passage p
            join source_document s on s.id = p.source_id and s.user_id = p.user_id
            where p.user_id = :userId
              and s.status = 'READY'
              and p.search_vector @@ plainto_tsquery('simple', :term)
            order by ts_rank_cd(p.search_vector, plainto_tsquery('simple', :term)) desc
            """, nativeQuery = true)
    List<PassageEntity> searchReadyPassages(
            @Param("userId") UUID userId,
            @Param("term") String term,
            Pageable pageable
    );

    @Query(value = """
            select p.*
            from passage p
            join source_document s on s.id = p.source_id and s.user_id = p.user_id
            where p.user_id = :userId
              and s.status = 'READY'
              and s.language = :sourceLanguage
              and p.search_vector @@ plainto_tsquery('simple', :term)
            order by ts_rank_cd(p.search_vector, plainto_tsquery('simple', :term)) desc,
                     abs(char_length(p.display_text) - :idealPassageCharacters),
                     s.created_at desc,
                     p.ordinal
            """, nativeQuery = true)
    List<PassageEntity> searchRankedReadyPassages(
            @Param("userId") UUID userId,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("term") String term,
            @Param("idealPassageCharacters") int idealPassageCharacters,
            Pageable pageable
    );
}
