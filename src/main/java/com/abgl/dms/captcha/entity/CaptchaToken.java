package com.abgl.dms.captcha.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "captcha_tokens")
public class CaptchaToken {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "answer_hmac", nullable = false)
    private String answerHmac;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    public CaptchaToken() {}

    public CaptchaToken(UUID id, String answerHmac, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.answerHmac = answerHmac;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAnswerHmac() { return answerHmac; }
    public void setAnswerHmac(String answerHmac) { this.answerHmac = answerHmac; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
