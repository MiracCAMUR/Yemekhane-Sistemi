package edu.kampus.yemekhane.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class OturumServisi {

    private static final String ON_EK = "oturum:";
    private static final Duration OTURUM_SURESI = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public OturumServisi(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String oturumOlustur(Long kullaniciId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(ON_EK + token, String.valueOf(kullaniciId), OTURUM_SURESI);
        return token;
    }

    public Long kullaniciIdGetir(String token) {
        if (token == null || token.isBlank()) return null;
        String val = redis.opsForValue().get(ON_EK + token);
        return val != null ? Long.valueOf(val) : null;
    }

    public void oturumSil(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(ON_EK + token);
        }
    }
}