package com.abgl.dms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AppUserRepository {

    private static final String FIND_FOR_AUTHENTICATION = """
            SELECT username, password_hash, enabled, locked_until
            FROM dms.app_user
            WHERE LOWER(username) = LOWER(?)
            """;

    private static final String FIND_PROFILE_BY_USERNAME = """
            SELECT display_name
            FROM dms.app_user
            WHERE LOWER(username) = LOWER(?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public AppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserDetails findForAuthentication(String username) {
        List<UserDetails> users = jdbcTemplate.query(FIND_FOR_AUTHENTICATION, (resultSet, rowNumber) -> {
            Timestamp lockedUntilTimestamp = resultSet.getTimestamp("locked_until");
            Instant lockedUntil = lockedUntilTimestamp == null ? null : lockedUntilTimestamp.toInstant();
            return User.withUsername(resultSet.getString("username"))
                    .password(resultSet.getString("password_hash"))
                    .authorities("ROLE_USER")
                    .disabled(!resultSet.getBoolean("enabled"))
                    .accountLocked(lockedUntil != null && lockedUntil.isAfter(Instant.now()))
                    .build();
        }, username);

        if (users.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        return users.getFirst();
    }

    public Optional<String> findDisplayNameByUsername(String username) {
        List<String> displayNames = jdbcTemplate.query(
                FIND_PROFILE_BY_USERNAME,
                (resultSet, rowNumber) -> resultSet.getString("display_name"),
                username);
        return displayNames.stream().findFirst();
    }
}
