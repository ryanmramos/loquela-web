package com.example.whatsanki.service.util;

import com.example.whatsanki.domain.SourceId;
import com.example.whatsanki.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ObjectKeyFactory {

    public String originalSource(UserId userId, SourceId sourceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        return "users/" + userId.value() + "/sources/" + sourceId.value() + "/original";
    }
}
