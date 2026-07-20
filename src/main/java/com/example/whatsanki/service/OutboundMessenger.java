package com.example.whatsanki.service;

import com.example.whatsanki.domain.ProviderMessageId;
import com.example.whatsanki.domain.WhatsAppAddress;
import com.example.whatsanki.dto.OutboundMessageDto;

public interface OutboundMessenger {
    ProviderMessageId send(WhatsAppAddress recipient, OutboundMessageDto message);
}
