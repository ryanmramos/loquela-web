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
        assertThat(flyway.info().applied()).hasSize(8);
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

    @Test
    void createsTenantSafeInboundSourceLink() {
        List<String> foreignKeyColumns = jdbcTemplate.queryForList("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.constraint_schema = kcu.constraint_schema
                where tc.table_schema = 'public'
                  and tc.table_name = 'source_document'
                  and tc.constraint_name = 'fk_source_document_inbound_owner'
                order by kcu.ordinal_position
                """, String.class);

        assertThat(foreignKeyColumns).containsExactly("inbound_message_id", "user_id");

        Integer ginIndexCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'passage'
                  and indexname = 'idx_passage_search_vector'
                  and indexdef ilike '% using gin %'
                """, Integer.class);
        assertThat(ginIndexCount).isEqualTo(1);
    }
}
