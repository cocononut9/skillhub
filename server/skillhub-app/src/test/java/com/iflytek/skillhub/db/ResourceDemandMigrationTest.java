package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Protects the already-applied plugin/prompt history when integrating newer feature branches. */
@Testcontainers
class ResourceDemandMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingPluginAndPromptDatabaseWithoutRepairOrDataLoss() throws Exception {
        var existing = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("51").load();
        existing.migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO user_account(id, display_name) VALUES ('migration-test', 'Migration test')");
            sql.executeUpdate("INSERT INTO namespace(id, slug, display_name, type) VALUES (901, 'migration-test', 'Migration test', 'TEAM')");
            sql.executeUpdate("INSERT INTO skill(id, namespace_id, slug, owner_id, resource_type) VALUES (901, 901, 'plugin-test', 'migration-test', 'PLUGIN'), (902, 901, 'prompt-test', 'migration-test', 'PROMPT')");
            try (var result = sql.executeQuery("SELECT checksum FROM flyway_schema_history WHERE version='50'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1860160560);
            }
            try (var result = sql.executeQuery("SELECT checksum FROM flyway_schema_history WHERE version='51'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(-72597489);
            }
            var integrated = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").load();
            assertThat(integrated.migrate().migrationsExecuted).isEqualTo(2);
            integrated.validate();
            assertThat(integrated.migrate().migrationsExecuted).isZero();
            try (var result = sql.executeQuery("SELECT count(*) FROM skill WHERE id IN (901,902) AND resource_type IN ('PLUGIN','PROMPT')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = sql.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('skill_usage_event','demand','demand_support','demand_supplement')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
        }
    }
}
