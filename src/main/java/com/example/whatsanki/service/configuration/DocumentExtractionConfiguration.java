package com.example.whatsanki.service.configuration;

import com.example.whatsanki.dto.WhatsAnkiConfigurationDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration(proxyBeanMethods = false)
public class DocumentExtractionConfiguration {

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
