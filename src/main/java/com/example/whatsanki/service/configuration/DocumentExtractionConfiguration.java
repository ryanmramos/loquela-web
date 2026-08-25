package com.example.whatsanki.service.configuration;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Provides a bounded executor for parsing attacker-controlled document bytes. */
@Configuration(proxyBeanMethods = false)
public class DocumentExtractionConfiguration {

    /** Creates named daemon workers so parsing timeouts do not stop application shutdown. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService documentExtractionExecutor(WhatsAnkiConfigurationDto configuration) {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(
                configuration.getIngestion().getExtractionThreads(),
                runnable -> {
                    Thread thread = new Thread(runnable, "document-extraction-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }
}
