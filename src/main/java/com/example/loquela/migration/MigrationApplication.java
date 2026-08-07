package com.example.loquela.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-shot Flyway entry point for the dedicated Railway migration service.
 * It deliberately has no component scan, web server, repositories, schedulers,
 * OAuth configuration, or provider clients.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataJpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MigrationApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ignored = application.run(args)) {
            // Flyway migration runs while the context initializes. Closing the
            // context makes this service a successful one-shot deployment.
        }
    }
}
