package com.noto.zhihui.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class AttachmentAccessTokenService {

    private static final String SEPARATOR = ".";
    /** 登录用户访问附件 URL 有效期（天） */
    private static final long OWNER_TTL_SECONDS = 7L * 24 * 3600;
    /** 分享链接内附件 URL 有效期（小时） */
    private static final long SHARE_TTL_SECONDS = 24L * 3600;

    private final String secret;

    public AttachmentAccessTokenService(
            @Value("${noto.security.jwt-secret:noto-zhihui-default-secret-key-32chars}") String secret
    ) {
        this.secret = secret;
    }

    public String generate(Long attachmentId) {
        return issue(attachmentId, null, OWNER_TTL_SECONDS);
    }

    public String generateForShare(Long attachmentId, String shareToken) {
        if (!StringUtils.hasText(shareToken)) {
            throw new IllegalArgumentException("shareToken required");
        }
        return issue(attachmentId, shareToken.trim(), SHARE_TTL_SECONDS);
    }

    public TokenClaims validate(Long attachmentId, String token, String shareToken) {
        if (attachmentId == null || !StringUtils.hasText(token)) {
            return null;
        }
        String trimmed = token.trim();
        int dot = trimmed.lastIndexOf(SEPARATOR.charAt(0));
        if (dot <= 0 || dot >= trimmed.length() - 1) {
            return null;
        }
        long expiryEpoch;
        try {
            expiryEpoch = Long.parseLong(trimmed.substring(0, dot));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (Instant.now().getEpochSecond() > expiryEpoch) {
            return null;
        }
        String signature = trimmed.substring(dot + 1);
        String boundShare = StringUtils.hasText(shareToken) ? shareToken.trim() : null;
        String payload = buildPayload(attachmentId, expiryEpoch, boundShare);
        if (!constantTimeEquals(sign(payload), signature)) {
            return null;
        }
        return new TokenClaims(attachmentId, expiryEpoch, boundShare);
    }

    private String issue(Long attachmentId, String shareToken, long ttlSeconds) {
        long expiryEpoch = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = buildPayload(attachmentId, expiryEpoch, shareToken);
        return expiryEpoch + SEPARATOR + sign(payload);
    }

    private String buildPayload(Long attachmentId, long expiryEpoch, String shareToken) {
        String base = attachmentId + ":" + expiryEpoch;
        if (StringUtils.hasText(shareToken)) {
            return base + ":share:" + shareToken;
        }
        return base;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(("attachment:" + payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Attachment token sign failed", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    public record TokenClaims(Long attachmentId, long expiryEpoch, String shareToken) {
        public boolean shareScoped() {
            return StringUtils.hasText(shareToken);
        }
    }
}
