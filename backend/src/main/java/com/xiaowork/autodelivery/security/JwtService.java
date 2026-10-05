package com.xiaowork.autodelivery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowork.autodelivery.model.Entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** Standards-compliant HS256 JWT. Authentication always rechecks account status and password revision. */
@Service
public class JwtService {
    private final byte[] secret;
    private final long hours;
    private final ObjectMapper json;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    public record Claims(long id, String passwordRevision) {}
    public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-hours:12}") long hours, ObjectMapper json) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must be set to at least 32 bytes");
        this.secret = secret.getBytes(StandardCharsets.UTF_8); this.hours=hours; this.json=json;
    }
    public String issue(User user) {
        try {
            String header=ENCODER.encodeToString(json.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));
            String payload=ENCODER.encodeToString(json.writeValueAsBytes(Map.of("sub",user.getId().toString(),"iat",Instant.now().getEpochSecond(),"exp",Instant.now().plusSeconds(hours*3600).getEpochSecond(),"pv",revision(user))));
            String content=header+"."+payload;
            return content+"."+ENCODER.encodeToString(sign(content));
        } catch(Exception ex) { throw new IllegalStateException("JWT generation failed",ex); }
    }
    public Claims verify(String token) {
        try {
            if(token.length()>4096) return null;
            String[] parts=token.split("\\.",-1);
            if(parts.length!=3 || !MessageDigest.isEqual(sign(parts[0]+"."+parts[1]),Base64.getUrlDecoder().decode(parts[2]))) return null;
            var header=json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            var payload=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if(!"HS256".equals(header.path("alg").asText()) || payload.path("exp").asLong(0)<=Instant.now().getEpochSecond()) return null;
            return new Claims(Long.parseLong(payload.path("sub").asText()),payload.path("pv").asText());
        } catch(Exception ex) { return null; }
    }
    private byte[] sign(String value) throws Exception { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
    public String revision(User user) {
        try { return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(user.getPasswordHash().getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
}
