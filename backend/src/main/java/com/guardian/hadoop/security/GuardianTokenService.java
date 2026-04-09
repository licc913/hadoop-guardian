package com.guardian.hadoop.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class GuardianTokenService {

    private final GuardianSecurityProperties properties;

    public GuardianTokenService(GuardianSecurityProperties properties) {
        this.properties = properties;
    }

    public String issueToken(GuardianAuthenticatedUser user) {
        long expiresAt = Instant.now().plusSeconds(properties.getTokenTtlHours() * 3600).getEpochSecond();
        String payload = String.join("\n", user.getUsername(), user.getDisplayName(), user.getRole(), String.valueOf(expiresAt));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payload);
        return encodedPayload + "." + signature;
    }

    public GuardianAuthenticatedUser parseToken(String token) {
        if (token == null || token.trim().isEmpty() || !token.contains(".")) {
            return null;
        }
        String[] parts = token.split("\\.", 2);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        String[] values = payload.split("\n", 4);
        if (values.length != 4) {
            return null;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(values[3]);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            return null;
        }
        return new GuardianAuthenticatedUser(values[0], values[1], values[2]);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign token", exception);
        }
    }
}
