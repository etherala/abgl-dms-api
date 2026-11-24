package com.abgl.dms.repository;

import com.abgl.dms.navigation.NavigationItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class NavigationRepository {

    private static final String FIND_MENUS_FOR_USER = """
            SELECT DISTINCT am.menu_code, am.menu_name, am.icon_name, am.sort_order, asm.sub_menu_code
            FROM dms.dms_user du
            JOIN dms.user_role ur ON ur.user_id = du.user_id
            JOIN dms."role" r ON r.role_id = ur.role_id
            JOIN dms.app_role_menus_map armm ON armm.role_code = r.role_code
            JOIN dms.app_menus am ON am.menu_code = armm.menu_code
            LEFT OUTER JOIN dms.app_sub_menus asm ON asm.menu_code = am.menu_code
            WHERE LOWER(du.username) = LOWER(?)
            ORDER BY am.sort_order
            """;

    private final JdbcTemplate jdbcTemplate;

    public NavigationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NavigationItem> findNavigationForUser(String username) {
        Map<String, NavigationItemBuilder> menus = new LinkedHashMap<>();
        jdbcTemplate.query(FIND_MENUS_FOR_USER, resultSet -> {
            String menuCode = resultSet.getString("menu_code");
            NavigationItemBuilder menu = menus.get(menuCode);
            if (menu == null) {
                menu = new NavigationItemBuilder(
                        menuCode,
                        resultSet.getString("menu_name"),
                        resultSet.getString("icon_name")
                );
                menus.put(menuCode, menu);
            }
            String subMenuCode = resultSet.getString("sub_menu_code");
            if (subMenuCode != null) {
                menu.subMenus.add(subMenuCode);
            }
        }, username);

        return menus.values().stream()
                .map(menu -> new NavigationItem(menu.id, menu.label, menu.icon, List.copyOf(menu.subMenus)))
                .toList();
    }

    private static class NavigationItemBuilder {
        private final String id;
        private final String label;
        private final String icon;
        private final Set<String> subMenus = new LinkedHashSet<>();

        private NavigationItemBuilder(String id, String label, String icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }
}
