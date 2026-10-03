package com.abgl.dms.captcha.service;

import com.abgl.dms.captcha.entity.CaptchaToken;
import com.abgl.dms.captcha.repository.CaptchaRepository;
import com.google.code.kaptcha.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

@Service
public class CaptchaService {

    private final Producer kaptchaProducer;
    private final CaptchaRepository repo;
    private final String hmacSecret;
    private final long ttlSeconds;

    public CaptchaService(Producer kaptchaProducer, CaptchaRepository repo,
                          @Value("${captcha.hmac-secret:change_me}") String hmacSecret,
                          @Value("${captcha.ttl-minutes:5}") long ttlMinutes) {
        this.kaptchaProducer = kaptchaProducer;
        this.repo = repo;
        this.hmacSecret = hmacSecret;
        this.ttlSeconds = ttlMinutes * 60;
    }

    @Transactional
    public GenerateResult generate() throws Exception {
        String text = kaptchaProducer.createText();
        BufferedImage img = kaptchaProducer.createImage(text);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(ttlSeconds);
        String hmac = hmacHex(normalize(text));
        CaptchaToken token = new CaptchaToken(id, hmac, now, expires);
        repo.save(token);
        return new GenerateResult(id.toString(), b64, expires.toString());
    }

    @Transactional
    public ValidateResult validate(String idStr, String answer) {
        try {
            UUID id = UUID.fromString(idStr);
            Optional<CaptchaToken> opt = repo.findById(id);
            if (opt.isEmpty()) return new ValidateResult(false, "not_found_or_expired");
            CaptchaToken token = opt.get();
            if (token.getExpiresAt().isBefore(Instant.now())) {
                repo.delete(token);
                return new ValidateResult(false, "expired");
            }
            String expected = token.getAnswerHmac();
            String actual = hmacHex(normalize(answer));
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                repo.delete(token);
                return new ValidateResult(true, "ok");
            } else {
                token.setAttempts(token.getAttempts() + 1);
                repo.save(token);
                if (token.getAttempts() >= 5) repo.delete(token);
                return new ValidateResult(false, "invalid");
            }
        } catch (IllegalArgumentException ex) {
            return new ValidateResult(false, "bad_id");
        } catch (Exception e) {
            return new ValidateResult(false, "error");
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private String hmacHex(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Scheduled(fixedRateString = "${captcha.cleanup-rate-ms:60000}")
    public void cleanupExpired() {
        repo.deleteByExpiresAtBefore(Instant.now());
    }

    public static record GenerateResult(String id, String imageBase64, String expiresAt) {}
    public static record ValidateResult(boolean valid, String message) {}
}
