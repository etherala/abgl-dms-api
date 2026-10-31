package com.abgl.dms.repository;

import com.abgl.dms.navigation.NavigationItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NavigationRepository {

    private static final String FIND_MENUS_FOR_USER = """
            SELECT DISTINCT am.menu_code, am.menu_name, am.icon_name, am.sort_order
            FROM dms.dms_user du
            JOIN dms.user_role ur ON ur.user_id = du.user_id
            JOIN dms."role" r ON r.role_id = ur.role_id
            JOIN dms.app_role_menus_map armm ON armm.role_code = r.role_code
            JOIN dms.app_menus am ON am.menu_code = armm.menu_code
            WHERE LOWER(du.username) = LOWER(?)
            ORDER BY am.sort_order
            """;

    private final JdbcTemplate jdbcTemplate;

    public NavigationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NavigationItem> findNavigationForUser(String username) {
        return jdbcTemplate.query(FIND_MENUS_FOR_USER, (resultSet, rowNumber) ->
                new NavigationItem(
                        resultSet.getString("menu_code"),
                        resultSet.getString("menu_name"),
                        resultSet.getString("icon_name")
                ), username);
    }
}
