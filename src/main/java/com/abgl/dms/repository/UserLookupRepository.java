package com.abgl.dms.repository;

import com.abgl.dms.userlookup.UserLookupItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserLookupRepository {

    private static final String SEARCH_ACTIVE_USERS = """
            SELECT username,
                   CONCAT_WS(' - ', NULLIF(first_name, ''), NULLIF(last_name, '')) AS full_name
            FROM dms.dms_user
            WHERE is_active = TRUE
              AND (
                    username ILIKE ? ESCAPE '\\'
                    OR first_name ILIKE ? ESCAPE '\\'
                    OR last_name ILIKE ? ESCAPE '\\'
                    OR CONCAT_WS(' - ', NULLIF(first_name, ''), NULLIF(last_name, ''))
                         ILIKE ? ESCAPE '\\'
              )
            ORDER BY first_name ASC NULLS LAST, last_name ASC NULLS LAST, username ASC
            LIMIT 50
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserLookupItem> searchActiveUsers(String searchText) {
        String pattern = escapeLikePattern(searchText) + "%";
        return jdbcTemplate.query(
                SEARCH_ACTIVE_USERS,
                (resultSet, rowNumber) -> new UserLookupItem(
                        resultSet.getString("username"),
                        resultSet.getString("full_name")),
                pattern, pattern, pattern, pattern);
    }

    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
