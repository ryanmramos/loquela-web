package com.example.whatsanki.service.exception;

public class UnsupportedBrowserLanguagePairException extends RuntimeException {

    public UnsupportedBrowserLanguagePairException() {
        super("The requested language pair is not supported");
    }
}
