package com.example.whatsanki.service.demo.impl;

import java.time.Instant;
import java.util.UUID;

interface DemoWorkspaceStore {
    DemoWorkspace create(Instant now);
    DemoWorkspace resolve(UUID reference, Instant now);
    DemoWorkspace reset(UUID reference, Instant now);
    void remove(UUID reference);
}
