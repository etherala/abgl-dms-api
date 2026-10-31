package com.abgl.dms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final JdbcTemplate jdbcTemplate;
    private final int maxFailedAttempts;
    private final long lockoutMinutes;

    public LoginAttemptService(
            JdbcTemplate jdbcTemplate,
            @Value("${auth.lockout.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${auth.lockout.duration-minutes:15}") long lockoutMinutes) {
        if (maxFailedAttempts < 1 || lockoutMinutes < 1) {
            throw new IllegalArgumentException("Lockout settings must be positive");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    @Transactional
    public void recordFailure(String username) {
        try {
            int updated = jdbcTemplate.update("""
                UPDATE dms.app_user
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE
                        WHEN failed_login_count + 1 >= ?
                        THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 minute')
                        ELSE locked_until
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE LOWER(username) = LOWER(?)
                    """, maxFailedAttempts, lockoutMinutes, username);
            log.info("Recorded failed login for username={}; accountUpdated={}", username, updated == 1);
        } catch (DataAccessException exception) {
            log.error("Could not record failed login for username={}", username, exception);
            throw exception;
        }
    }

    @Transactional
    public void recordSuccess(String username) {
        try {
            int updated = jdbcTemplate.update("""
                UPDATE dms.app_user
                SET failed_login_count = 0,
                    locked_until = NULL,
                    last_login_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE LOWER(username) = LOWER(?)
                    """, username);
            if (updated == 1) {
                log.debug("Reset login failure state for username={}", username);
            } else {
                log.warn("Could not update successful-login state; account not found for username={}", username);
            }
        } catch (DataAccessException exception) {
            log.error("Could not record successful login for username={}", username, exception);
            throw exception;
        }
    }
}
