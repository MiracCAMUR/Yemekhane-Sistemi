package edu.kampus.yemekhane.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OturumServisi {

    private final Map<String, Long> oturumlar = new ConcurrentHashMap<>();

    public String oturumOlustur(Long kullaniciId) {
        String token = UUID.randomUUID().toString();
        oturumlar.put(token, kullaniciId);
        return token;
    }

    public Long kullaniciIdGetir(String token) {
        if (token == null || token.isBlank()) return null;
        return oturumlar.get(token);
    }

    public void oturumSil(String token) {
        if (token != null) oturumlar.remove(token);
    }
}