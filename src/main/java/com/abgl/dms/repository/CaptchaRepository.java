package com.abgl.dms.repository;

import com.abgl.dms.entity.CaptchaToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface CaptchaRepository extends JpaRepository<CaptchaToken, UUID> {
    void deleteByExpiresAtBefore(Instant time);
}
