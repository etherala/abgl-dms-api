package com.abgl.dms.repository;

import com.abgl.dms.entitylookup.EntityLookupItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EntityLookupRepository {

    private static final String FIND_VISIBLE_ENTITIES = """
            SELECT DISTINCT em.entity_code, em.entity_name, em.entity_type
            FROM dms.dms_user du
            JOIN dms.user_entity_visibility uev ON uev.user_code = du.username
            JOIN dms.entity_master em ON em.entity_code = uev.entity_code
            WHERE LOWER(du.username) = LOWER(?)
              AND (
                    em.entity_code ILIKE ? ESCAPE '\\'
                    OR em.entity_name ILIKE ? ESCAPE '\\'
              )
            ORDER BY em.entity_name ASC, em.entity_code ASC
            LIMIT 50
            """;

    private final JdbcTemplate jdbcTemplate;

    public EntityLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EntityLookupItem> findVisibleEntities(String username, String prefix) {
        String pattern = escapeLikePattern(prefix) + "%";
        return jdbcTemplate.query(FIND_VISIBLE_ENTITIES, (resultSet, rowNumber) ->
                new EntityLookupItem(
                        resultSet.getString("entity_code"),
                        resultSet.getString("entity_name"),
                        resultSet.getString("entity_type")
                ), username, pattern, pattern);
    }

    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
