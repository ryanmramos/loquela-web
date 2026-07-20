package com.example.whatsanki.service.twilio;

import com.example.whatsanki.service.twilio.TwiMLService;
import org.springframework.stereotype.Service;

@Service
public class TwiMLServiceImpl implements TwiMLService {

    @Override
    public String message(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>"
                + escapeXml(body)
                + "</Message></Response>";
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
