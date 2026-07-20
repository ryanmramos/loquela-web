package com.example.whatsanki.service.dictionary;

/**
 * Resolves the provider selected for this application instance.
 *
 * <p>The seam keeps lookup orchestration independent from Spring bean names and makes
 * adding another provider an implementation-and-configuration change.</p>
 */
public interface DictionaryProviderSelector {

    DictionaryProvider activeProvider();
}
