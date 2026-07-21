package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.domain.JobId;
import com.example.whatsanki.domain.UserId;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.dto.DeleteUserDataJobPayloadDto;
import com.example.whatsanki.service.AccountDeletionService;
import com.example.whatsanki.service.exception.AccountDeletionException;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeleteUserDataBackgroundJobHandlerTest {

    private final AccountDeletionService accountDeletionService = mock(AccountDeletionService.class);
    private final DeleteUserDataBackgroundJobHandler handler = new DeleteUserDataBackgroundJobHandler(accountDeletionService);

    @Test
    void delegatesAValidUserOwnedPayload() {
        UserId userId = UserId.random();
        ClaimedBackgroundJobDto job = job(userId, DeleteUserDataJobPayloadDto.create(userId).toMap());

        assertThatCode(() -> handler.handle(job)).doesNotThrowAnyException();
        verify(accountDeletionService).delete(userId, job.jobId());
    }

    @Test
    void preservesTransientCleanupFailuresForTheRunner() {
        UserId userId = UserId.random();
        ClaimedBackgroundJobDto job = job(userId, DeleteUserDataJobPayloadDto.create(userId).toMap());
        doThrow(new AccountDeletionException(BackgroundJobFailureKind.TRANSIENT, "USER_DATA_BUSY"))
                .when(accountDeletionService).delete(userId, job.jobId());

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.TRANSIENT);
                    assertThat(exception.errorCode()).isEqualTo("USER_DATA_BUSY");
                });
    }

    @Test
    void permanentlyRejectsAPayloadWhoseUserDoesNotMatchTheJobOwner() {
        ClaimedBackgroundJobDto job = job(UserId.random(), DeleteUserDataJobPayloadDto.create(UserId.random()).toMap());

        assertThatThrownBy(() -> handler.handle(job))
                .isInstanceOfSatisfying(BackgroundJobExecutionException.class, exception -> {
                    assertThat(exception.failureKind()).isEqualTo(BackgroundJobFailureKind.PERMANENT);
                    assertThat(exception.errorCode()).isEqualTo("INVALID_JOB_PAYLOAD");
                });
    }

    private ClaimedBackgroundJobDto job(UserId userId, Map<String, Object> payload) {
        return new ClaimedBackgroundJobDto(JobId.random(), userId.value(), BackgroundJobEntity.Type.DELETE_USER_DATA, payload, 1, 3);
    }
}
