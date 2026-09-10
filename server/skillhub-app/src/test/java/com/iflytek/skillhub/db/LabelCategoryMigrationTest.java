package com.iflytek.skillhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.skill.ResourceType;
import com.iflytek.skillhub.search.LabelMatchMode;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import com.iflytek.skillhub.search.postgres.PostgresFullTextQueryService;
import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the migration and production search SQL against an isolated PostgreSQL database. */
@Testcontainers
class LabelCategoryMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void preservesExistingLabelsAndFiltersAllGroupsBeforePagination() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("53").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO user_account(id, display_name) VALUES ('labels-test', 'Labels test')");
            sql.executeUpdate("INSERT INTO namespace(id, slug, display_name, type) VALUES (900, 'labels-test', 'Labels test', 'TEAM')");
            sql.executeUpdate("""
                    INSERT INTO skill(id, namespace_id, slug, owner_id, resource_type, display_name, download_count)
                    SELECT id, 900, 'report-' || id, 'labels-test', CASE WHEN id=905 THEN 'PLUGIN' ELSE 'WEB' END,
                           'report', id FROM generate_series(901,907) AS id
                    """);
            sql.executeUpdate("UPDATE skill SET hidden=TRUE WHERE id=906");
            sql.executeUpdate("UPDATE skill SET visibility='PRIVATE' WHERE id=907");
            sql.executeUpdate("INSERT INTO label_definition(slug, type) VALUES ('influencer-screening', 'RECOMMENDED'), ('official', 'PRIVILEGED')");
            sql.executeUpdate("""
                    INSERT INTO label_translation(label_id, locale, display_name)
                    SELECT id, 'zh', CASE WHEN slug='influencer-screening' THEN '品牌专员' ELSE '官方认证' END
                    FROM label_definition
                    """);
            sql.executeUpdate("""
                    INSERT INTO skill_label(skill_id, label_id)
                    SELECT 901, id FROM label_definition WHERE slug='influencer-screening'
                    """);
            var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").target("54").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var rows = sql.executeQuery("SELECT category, COUNT(*) FROM label_definition GROUP BY category ORDER BY category")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("GENERAL");
                assertThat(rows.getInt(2)).isEqualTo(1);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("ROLE");
                assertThat(rows.getInt(2)).isEqualTo(10);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("WORKFLOW");
                assertThat(rows.getInt(2)).isEqualTo(10);
            }
            try (var rows = sql.executeQuery("""
                    SELECT d.slug, d.category, t.display_name FROM skill_label sl
                    JOIN label_definition d ON d.id=sl.label_id JOIN label_translation t ON t.label_id=d.id
                    WHERE sl.skill_id=901 AND t.locale='zh'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("influencer-screening");
                assertThat(rows.getString(2)).isEqualTo("ROLE");
                assertThat(rows.getString(3)).isEqualTo("品牌专员");
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM label_definition WHERE slug='role-brand-specialist'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            sql.executeUpdate("""
                    INSERT INTO skill_label(skill_id, label_id)
                    SELECT s.id, d.id FROM skill s CROSS JOIN label_definition d
                    WHERE (d.slug='influencer-screening' AND s.id IN (902,903,905,906,907))
                       OR (d.slug='workflow-brand-marketing' AND s.id IN (901,902,904,905,906,907))
                    """);
            sql.executeUpdate("""
                    INSERT INTO skill_search_document(skill_id, namespace_id, namespace_slug, owner_id, title, visibility, status)
                    SELECT id, namespace_id, 'labels-test', owner_id, display_name, visibility, status FROM skill
                    """);
        }
        try (var factory = new Configuration()
                .setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .setProperty("hibernate.connection.username", POSTGRES.getUsername())
                .setProperty("hibernate.connection.password", POSTGRES.getPassword())
                .buildSessionFactory();
             var manager = factory.createEntityManager()) {
            var search = new PostgresFullTextQueryService(manager);
            var labels = List.of("workflow-brand-marketing", "influencer-screening");
            var first = search.search(new SearchQuery("report", 900L, SearchVisibilityScope.anonymous(),
                    "downloads", 0, 1, labels, false, ResourceType.WEB, LabelMatchMode.ALL));
            assertThat(first.total()).isEqualTo(2);
            assertThat(first.skillIds()).containsExactly(902L);
            var second = search.search(new SearchQuery("report", 900L, SearchVisibilityScope.anonymous(),
                    "downloads", 1, 1, labels, false, ResourceType.WEB, LabelMatchMode.ALL));
            assertThat(second.total()).isEqualTo(2);
            assertThat(second.skillIds()).containsExactly(901L);
            var legacy = search.search(new SearchQuery("report", 900L, SearchVisibilityScope.anonymous(),
                    "downloads", 0, 12, labels, false, ResourceType.WEB));
            assertThat(legacy.total()).isEqualTo(4);
            var missing = search.search(new SearchQuery(null, 900L, SearchVisibilityScope.anonymous(),
                    "newest", 0, 12, List.of("missing", "influencer-screening"), false, null, LabelMatchMode.ALL));
            assertThat(missing.total()).isZero();
        }
    }
}
