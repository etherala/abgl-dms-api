package com.abgl.dms.captcha.repository;

import com.abgl.dms.captcha.entity.CaptchaToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface CaptchaRepository extends JpaRepository<CaptchaToken, UUID> {
    void deleteByExpiresAtBefore(Instant time);
}
