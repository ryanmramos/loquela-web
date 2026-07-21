package com.example.whatsanki.service.account;

import com.example.whatsanki.domain.AppUserEntity;
import com.example.whatsanki.domain.ExportRequestEntity;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.SourceDocumentEntity;
import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.repository.AppUserRepository;
import com.example.whatsanki.repository.BackgroundJobRepository;
import com.example.whatsanki.repository.ExportRequestRepository;
import com.example.whatsanki.repository.SourceDocumentRepository;
import com.example.whatsanki.service.exception.AccountDeletionException;
import com.example.whatsanki.service.util.ObjectKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionCleanupServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;
    @Mock private SourceDocumentRepository sourceDocumentRepository;
    @Mock private ExportRequestRepository exportRequestRepository;

    private AccountDeletionCleanupService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionCleanupService(
                appUserRepository,
                backgroundJobRepository,
                sourceDocumentRepository,
                exportRequestRepository,
                new ObjectKeyFactory()
        );
    }

    @Test
    void collectsRecordedAndDeterministicKeysBeforeDatabaseCascade() {
        UserId userId = UserId.random();
        JobId jobId = JobId.random();
        SourceDocumentEntity recorded = source(userId, SourceDocumentEntity.Kind.PDF, "stored/source");
        SourceDocumentEntity unrecorded = source(userId, SourceDocumentEntity.Kind.PDF, null);
        ExportRequestEntity export = new ExportRequestEntity();
        export.setId(java.util.UUID.randomUUID());
        export.setArtifactKey("stored/export");
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(deletingUser(userId)));
        when(backgroundJobRepository.countRunningForUser(userId.value(), jobId.value())).thenReturn(0L);
        when(sourceDocumentRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value()))
                .thenReturn(List.of(recorded, unrecorded));
        when(exportRequestRepository.findAllByUserIdOrderByCreatedAtDesc(userId.value())).thenReturn(List.of(export));

        Set<String> keys = service.collectObjectKeys(userId, jobId);

        assertThat(keys).contains(
                "stored/source",
                new ObjectKeyFactory().originalSource(userId, new SourceId(unrecorded.getId())),
                "stored/export",
                new ObjectKeyFactory().exportedDeck(userId, new com.example.whatsanki.domain.ExportRequestId(export.getId()))
        );
    }

    @Test
    void waitsForOtherRunningJobsBeforeDeletingAnyObjects() {
        UserId userId = UserId.random();
        JobId jobId = JobId.random();
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(deletingUser(userId)));
        when(backgroundJobRepository.countRunningForUser(userId.value(), jobId.value())).thenReturn(1L);

        assertThatThrownBy(() -> service.collectObjectKeys(userId, jobId))
                .isInstanceOfSatisfying(AccountDeletionException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("USER_DATA_BUSY");
                });
        verify(sourceDocumentRepository, never()).findAllByUserIdOrderByCreatedAtDesc(userId.value());
    }

    @Test
    void removesTheUserOnlyAfterTheFinalQuiescenceCheck() {
        UserId userId = UserId.random();
        JobId jobId = JobId.random();
        AppUserEntity user = deletingUser(userId);
        when(appUserRepository.findById(userId.value())).thenReturn(Optional.of(user));
        when(backgroundJobRepository.countRunningForUser(userId.value(), jobId.value())).thenReturn(0L);

        service.deleteUserAfterObjects(userId, jobId);

        verify(appUserRepository).delete(user);
    }

    private SourceDocumentEntity source(UserId userId, SourceDocumentEntity.Kind kind, String objectKey) {
        SourceDocumentEntity source = new SourceDocumentEntity();
        source.setId(java.util.UUID.randomUUID());
        source.setUserId(userId.value());
        source.setKind(kind);
        source.setObjectKey(objectKey);
        return source;
    }

    private AppUserEntity deletingUser(UserId userId) {
        AppUserEntity user = new AppUserEntity();
        user.setId(userId.value());
        user.setStatus(AppUserEntity.Status.DELETING);
        return user;
    }
}
