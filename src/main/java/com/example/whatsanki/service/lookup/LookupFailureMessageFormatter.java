package com.example.whatsanki.service.lookup;

/** Converts an internal safe lookup error code into user-facing WhatsApp guidance. */
public interface LookupFailureMessageFormatter {
    String format(String errorCode);
}
