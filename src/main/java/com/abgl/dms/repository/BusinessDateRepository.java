package com.abgl.dms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class BusinessDateRepository {

    private static final String FIND_ACTIVE_BUSINESS_DATE = """
            SELECT business_date
            FROM dms.app_dates
            WHERE branch_code = ?
              AND is_active = TRUE
              AND is_deleted = FALSE
            ORDER BY business_date DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public BusinessDateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LocalDate> findActiveBusinessDate(String branchCode) {
        List<LocalDate> dates = jdbcTemplate.query(
                FIND_ACTIVE_BUSINESS_DATE,
                (resultSet, rowNumber) -> resultSet.getObject("business_date", LocalDate.class),
                branchCode);
        return dates.stream().findFirst();
    }
}
