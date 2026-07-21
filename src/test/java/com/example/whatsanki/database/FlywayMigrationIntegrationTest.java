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
        assertThat(flyway.info().applied()).hasSize(16);
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
                "outbound_message", "account_deletion_request",
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

        Integer sourceLifecycleIndexCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'background_job'
                  and indexname = 'idx_background_job_source_reference'
                """, Integer.class);
        assertThat(sourceLifecycleIndexCount).isEqualTo(1);

        List<String> sourceColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'source_document'
                """, String.class);
        assertThat(sourceColumns).contains("version", "original_stored_at");

        List<String> inboundColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'inbound_message'
                """, String.class);
        assertThat(inboundColumns).contains("twilio_media_sid");

        String sourceStatusConstraint = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'ck_source_document_status'
                """, String.class);
        assertThat(sourceStatusConstraint).contains("STORED");

        String sourceStorageTimeConstraint = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'ck_source_document_object_storage_time'
                """, String.class);
        assertThat(sourceStorageTimeConstraint).contains("original_stored_at");

        String jobTypeConstraint = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid)
                from pg_constraint
                where conname = 'ck_background_job_type'
                """, String.class);
        assertThat(jobTypeConstraint)
                .contains(
                        "EXTRACT_DOCUMENT", "DELETE_TWILIO_MEDIA", "PURGE_SOURCE_OBJECT",
                        "DELIVER_LOOKUP_RESULT"
                );

        List<String> lookupColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'lookup_session'
                """, String.class);
        assertThat(lookupColumns).contains("inbound_message_id", "status", "error_code", "updated_at");

        List<String> cardColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'card'
                """, String.class);
        assertThat(cardColumns).contains(
                "saved_from_lookup_session_id", "saved_from_context_number", "anki_template_version"
        );

        Integer savedCardOriginIndexCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'card'
                  and indexname = 'uk_card_user_saved_lookup_context'
                  and indexdef ilike '% unique %'
                """, Integer.class);
        assertThat(savedCardOriginIndexCount).isEqualTo(1);
    }
}
