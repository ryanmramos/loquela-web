package com.example.whatsanki.service;

/** Converts an internal safe lookup error code into user-facing WhatsApp guidance. */
public interface LookupFailureMessageFormatter {
    String format(String errorCode);
}
