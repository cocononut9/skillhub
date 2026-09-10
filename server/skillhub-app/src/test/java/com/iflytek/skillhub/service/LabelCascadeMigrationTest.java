package com.iflytek.skillhub.service;

import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;

class LabelCascadeMigrationTest {
    @Test
    void deletingLibraryLabelCascadesAllAttachmentsAndTranslationsButKeepsResources() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:label-cascade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        jdbc.execute("CREATE TABLE user_account (id VARCHAR(128) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE skill (id BIGINT PRIMARY KEY)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V34__skill_label_system.sql")).execute(dataSource);
        jdbc.update("INSERT INTO user_account VALUES ('admin')");
        jdbc.update("INSERT INTO skill VALUES (1), (2)");
        jdbc.update("INSERT INTO label_definition (id, slug, type, created_by) VALUES (10, 'marketing', 'RECOMMENDED', 'admin')");
        jdbc.update("INSERT INTO label_translation (label_id, locale, display_name) VALUES (10, 'zh', '营销')");
        jdbc.update("INSERT INTO skill_label (skill_id, label_id) VALUES (1, 10), (2, 10)");
        jdbc.update("DELETE FROM label_definition WHERE id = 10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skill_label", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM label_translation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skill", Integer.class)).isEqualTo(2);
    }
}
