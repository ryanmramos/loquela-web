package com.example.whatsanki.service.job;

import com.example.whatsanki.domain.BackgroundJobEntity;
import com.example.whatsanki.domain.BackgroundJobFailureKind;
import com.example.whatsanki.dto.ClaimedBackgroundJobDto;
import com.example.whatsanki.service.job.BackgroundJobDispatcher;
import com.example.whatsanki.service.exception.BackgroundJobExecutionException;
import com.example.whatsanki.service.job.BackgroundJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BackgroundJobDispatcherImpl implements BackgroundJobDispatcher {

    private final Map<BackgroundJobEntity.Type, BackgroundJobHandler> handlers;

    public BackgroundJobDispatcherImpl(List<BackgroundJobHandler> registeredHandlers) {
        EnumMap<BackgroundJobEntity.Type, BackgroundJobHandler> mappedHandlers =
                new EnumMap<>(BackgroundJobEntity.Type.class);
        for (BackgroundJobHandler handler : registeredHandlers) {
            BackgroundJobHandler duplicate = mappedHandlers.put(handler.supportedType(), handler);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple background job handlers registered for " + handler.supportedType()
                );
            }
        }
        handlers = Map.copyOf(mappedHandlers);
        log.info("Registered background job handlers count={}", handlers.size());
    }

    @Override
    public void dispatch(ClaimedBackgroundJobDto job) {
        BackgroundJobHandler handler = handlers.get(job.type());
        if (handler == null) {
            throw new BackgroundJobExecutionException(
                    BackgroundJobFailureKind.PERMANENT,
                    "NO_JOB_HANDLER",
                    null
            );
        }
        handler.handle(job);
    }
}
