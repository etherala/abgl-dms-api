package com.abgl.dms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final Pattern BROWSER_VERSION = Pattern.compile(
            "(Edg|OPR|Chrome|Firefox|Version|MSIE|rv)(?::|/|\\s)?([0-9]+(?:\\.[0-9]+)*)");

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
    public void recordFailure(
            String username, String failureReason, HttpServletRequest request, boolean countAsBadCredential) {
        try {
            if (countAsBadCredential) {
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
            }
            insertLoginAudit(username, null, "FAILED", null, failureReason, request);
        } catch (DataAccessException exception) {
            log.error("Could not record failed login for username={}", username, exception);
            throw exception;
        }
    }

    @Transactional
    public void recordSuccess(String username, String sessionId, HttpServletRequest request) {
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
                throw new IllegalStateException("Authenticated account was not found while recording login");
            }
            insertLoginAudit(username, sessionId, "SUCCESS", null, null, request);
        } catch (DataAccessException exception) {
            log.error("Could not record successful login for username={}", username, exception);
            throw exception;
        }
    }

    @Transactional
    public void recordLogout(String username, String sessionId) {
        int updated = jdbcTemplate.update("""
                UPDATE dms.app_user_login_audit
                SET logout_time = CURRENT_TIMESTAMP,
                    last_activity_time = CURRENT_TIMESTAMP,
                    session_duration = GREATEST(
                        0, FLOOR(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - login_time)))::BIGINT),
                    logout_type = 'USER_INITIATED'
                WHERE username = ?
                  AND session_id = ?
                  AND login_status = 'SUCCESS'
                  AND logout_time IS NULL
                """, username, sessionId);
        if (updated != 1) {
            log.warn("Logout audit row not updated for username={}; updatedRows={}", username, updated);
        }
    }

    private void insertLoginAudit(
            String username,
            String sessionId,
            String loginStatus,
            String logoutType,
            String failureReason,
            HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String browser = browserName(userAgent);
        String browserVersion = browserVersion(userAgent);
        String operatingSystem = operatingSystem(userAgent);
        String deviceType = deviceType(userAgent);

        jdbcTemplate.update("""
                INSERT INTO dms.app_user_login_audit (
                    user_id, username, session_id, login_type, login_status,
                    login_time, last_activity_time, ip_address, user_agent,
                    browser_name, browser_version, operating_system, device_type,
                    failure_reason, logout_type, created_by
                )
                VALUES (
                    (SELECT user_id FROM dms.app_user WHERE LOWER(username) = LOWER(?)),
                    ?, ?, 'PASSWORD', ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                username, username, sessionId, loginStatus, request.getRemoteAddr(),
                userAgent, browser, browserVersion, operatingSystem, deviceType,
                failureReason, logoutType, username);
    }

    private static String browserName(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        if (userAgent.contains("Edg/")) return "Edge";
        if (userAgent.contains("OPR/")) return "Opera";
        if (userAgent.contains("Chrome/")) return "Chrome";
        if (userAgent.contains("Firefox/")) return "Firefox";
        if (userAgent.contains("Safari/")) return "Safari";
        if (userAgent.contains("MSIE") || userAgent.contains("Trident/")) return "Internet Explorer";
        return "Unknown";
    }

    private static String browserVersion(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String browserToken = userAgent.contains("Edg/") ? "Edg"
                : userAgent.contains("OPR/") ? "OPR"
                : userAgent.contains("Chrome/") ? "Chrome"
                : userAgent.contains("Firefox/") ? "Firefox"
                : userAgent.contains("Version/") ? "Version"
                : userAgent.contains("MSIE") ? "MSIE"
                : userAgent.contains("rv:") ? "rv" : null;
        if (browserToken == null) {
            return null;
        }
        Matcher matcher = BROWSER_VERSION.matcher(userAgent);
        while (matcher.find()) {
            if (browserToken.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private static String operatingSystem(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac OS")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        return "Unknown";
    }

    private static String deviceType(String userAgent) {
        if (userAgent == null) return "UNKNOWN";
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("ipad") || normalized.contains("tablet")) return "TABLET";
        if (normalized.contains("mobile") || normalized.contains("iphone")
                || normalized.contains("android")) return "MOBILE";
        return "DESKTOP";
    }
}
