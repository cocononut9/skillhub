package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQL read projections join authors and aggregate support counts without per-card queries. */
@Repository
@Transactional(readOnly = true)
public class DemandQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SELECT = """
            SELECT d.*, u.display_name AS author_name,
              (SELECT count(*) FROM demand_support s WHERE s.demand_id=d.id) AS support_count,
              EXISTS(SELECT 1 FROM demand_support s WHERE s.demand_id=d.id AND s.user_id=:viewer) AS supported,
              (SELECT count(*) FROM demand_supplement c WHERE c.demand_id=d.id AND c.hidden=false) AS supplement_count
            FROM demand d JOIN user_account u ON u.id=d.author_id
            """;

    public DemandQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DemandPageResponse list(String viewer, String keyword, String category, boolean mine,
                                   boolean includeHidden, String sort, int page, int size) {
        String where = " WHERE (:includeHidden OR d.hidden=false)"
                + " AND (:mine=false OR d.author_id=:viewer)"
                + " AND (:category='' OR d.category=:category)"
                + " AND (LOWER(d.title) LIKE :keyword ESCAPE '!' OR LOWER(d.scenario) LIKE :keyword ESCAPE '!')";
        var params = new MapSqlParameterSource().addValue("viewer", viewer)
                .addValue("includeHidden", includeHidden).addValue("mine", mine)
                .addValue("category", category.strip()).addValue("keyword", "%" + escape(keyword.strip().toLowerCase(java.util.Locale.ROOT)) + "%")
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM demand d" + where, params, Long.class);
        String order = "popular".equals(sort) ? "support_count DESC, d.created_at DESC, d.id DESC" : "d.created_at DESC, d.id DESC";
        var items = jdbc.query(SELECT + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                params, (rs, row) -> demand(rs, viewer));
        return new DemandPageResponse(items, total == null ? 0 : total, page, size);
    }

    public DemandResponse detail(Long id, String viewer, boolean admin) {
        return jdbc.query(SELECT + " WHERE d.id=:id AND (:admin OR d.hidden=false)",
                Map.of("id", id, "viewer", viewer, "admin", admin), (rs, row) -> demand(rs, viewer))
                .stream().findFirst().orElseThrow(() -> new DomainNotFoundException("error.demand.notFound"));
    }

    public DemandSupplementPageResponse supplements(Long id, String viewer, boolean admin, int page, int size) {
        // Parent visibility also applies when a client directly requests the supplements endpoint.
        detail(id, viewer, admin);
        String where = " WHERE c.demand_id=:id AND (:admin OR c.hidden=false)";
        var params = new MapSqlParameterSource().addValue("id", id).addValue("admin", admin)
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM demand_supplement c" + where, params, Long.class);
        var items = jdbc.query("SELECT c.*, u.display_name AS author_name FROM demand_supplement c"
                        + " JOIN user_account u ON u.id=c.author_id" + where
                        + " ORDER BY c.created_at, c.id LIMIT :limit OFFSET :offset", params,
                (rs, row) -> new DemandSupplementResponse(rs.getLong("id"), rs.getString("content"),
                        rs.getString("author_name"), viewer.equals(rs.getString("author_id")), rs.getBoolean("hidden"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
        return new DemandSupplementPageResponse(items, total == null ? 0 : total, page, size);
    }

    private DemandResponse demand(ResultSet rs, String viewer) throws SQLException {
        return new DemandResponse(rs.getLong("id"), rs.getString("title"), rs.getString("scenario"),
                rs.getString("expected_result"), rs.getString("category"), rs.getString("frequency"),
                rs.getString("current_time_cost"), rs.getString("usage_scope"), rs.getString("author_name"),
                viewer.equals(rs.getString("author_id")), rs.getBoolean("hidden"), rs.getLong("support_count"),
                rs.getBoolean("supported"), rs.getLong("supplement_count"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String escape(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
}
