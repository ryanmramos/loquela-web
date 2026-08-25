package com.example.whatsanki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Starts the API and enables the scheduled workers that advance queued jobs. */
@SpringBootApplication
@EnableScheduling
public class WhatsAnkiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAnkiApplication.class, args);
    }

}
