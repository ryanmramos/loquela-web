package com.example.whatsanki.database;

import com.example.whatsanki.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FlywayMigrationIntegrationTest extends PostgreSqlIntegrationTest {

    private final Flyway flyway;
    private final JdbcTemplate jdbcTemplate;

    FlywayMigrationIntegrationTest(Flyway flyway, JdbcTemplate jdbcTemplate) {
        this.flyway = flyway;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void validatesAllMigrationsAgainstPostgreSql() {
        assertThatCode(flyway::validate).doesNotThrowAnyException();
        assertThat(flyway.info().applied()).hasSize(7);
    }

    @Test
    void createsAllExpectedTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                order by table_name
                """, String.class);

        assertThat(tables).contains(
                "app_user", "inbound_message", "source_document", "passage",
                "background_job", "lookup_session", "deck", "card", "export_request",
                "flyway_schema_history"
        );
    }
}
