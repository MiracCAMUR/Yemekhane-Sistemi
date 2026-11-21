package edu.kampus.yemekhane.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kampus.yemekhane.exception.IsKuraliHatasi;
import edu.kampus.yemekhane.repository.YemekhaneRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
public class YemekhaneServisi {

    private static final String MENU_ON_EK = "menu:";
    private static final Duration MENU_TTL = Duration.ofMinutes(5);

    private final YemekhaneRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public YemekhaneServisi(YemekhaneRepository repo, PasswordEncoder passwordEncoder, StringRedisTemplate redis) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    public Map<String, Object> girisYap(String eposta, String sifre) {
        if (eposta == null || eposta.isBlank() || sifre == null || sifre.isBlank()) {
            throw new IsKuraliHatasi("E-posta ve şifre zorunludur.");
        }
        List<Map<String, Object>> sonuc = repo.kullaniciBulEpostaIle(eposta.trim().toLowerCase(Locale.ROOT));
        if (sonuc.isEmpty()) {
            throw new IsKuraliHatasi("E-posta veya şifre hatalı.", 401);
        }
        Map<String, Object> k = sonuc.get(0);
        if (Boolean.FALSE.equals(k.get("aktif")) || "0".equals(String.valueOf(k.get("aktif")))) {
            throw new IsKuraliHatasi("Hesabınız pasif durumda.", 403);
        }
        String hash = String.valueOf(k.get("sifre_hash"));
        if (!passwordEncoder.matches(sifre, hash)) {
            throw new IsKuraliHatasi("E-posta veya şifre hatalı.", 401);
        }
        Map<String, Object> temiz = new LinkedHashMap<>(k);
        temiz.remove("sifre_hash");
        return temiz;
    }

    public Map<String, Object> aktifKullaniciBilgisi(Long kullaniciId) {
        List<Map<String, Object>> sonuc = repo.kullaniciBulIdIleKaynaktan(kullaniciId);
        if (sonuc.isEmpty()) {
            throw new IsKuraliHatasi("Kullanıcı bulunamadı.", 401);
        }
        return sonuc.get(0);
    }

    public List<Map<String, Object>> menuleriGetir(String baslangic, String bitis, Integer yemekhaneId) {
        String bas = (baslangic == null || baslangic.isBlank()) ? LocalDate.now().toString() : baslangic;
        String bit = (bitis == null || bitis.isBlank()) ? bas : bitis;

        List<Map<String, Object>> menuler = repo.tarihAraligiMenuleriGetir(bas, bit, yemekhaneId);
        if (menuler.isEmpty()) return List.of();

        List<Long> menuIds = menuler.stream()
                .map(m -> ((Number) m.get("menu_id")).longValue())
                .toList();

        List<Map<String, Object>> tumYemekler = repo.menuYemekleriniGetir(menuIds);
        Map<Long, List<Map<String, Object>>> yemekHaritasi = new HashMap<>();
        for (Map<String, Object> y : tumYemekler) {
            Long mId = ((Number) y.get("menu_id")).longValue();
            yemekHaritasi.computeIfAbsent(mId, k -> new ArrayList<>()).add(y);
        }

        List<Map<String, Object>> sonuc = new ArrayList<>();
        for (Map<String, Object> m : menuler) {
            Map<String, Object> kart = new LinkedHashMap<>(m);
            Long mId = ((Number) m.get("menu_id")).longValue();
            List<Map<String, Object>> yemekler = yemekHaritasi.getOrDefault(mId, List.of());
            int toplamKalori = yemekler.stream()
                    .mapToInt(y -> ((Number) y.get("kalori")).intValue())
                    .sum();
            kart.put("yemekler", yemekler);
            kart.put("toplam_kalori", toplamKalori);
            sonuc.add(kart);
        }
        return sonuc;
    }

    public void menuOnbellegiTemizle() {
        try {
            Set<String> anahtarlar = redis.keys(MENU_ON_EK + "*");
            if (anahtarlar != null && !anahtarlar.isEmpty()) {
                redis.delete(anahtarlar);
            }
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> menulerOnbellekli(String baslangic, String bitis, Integer yemekhaneId) {
        String bas = (baslangic == null || baslangic.isBlank()) ? LocalDate.now().toString() : baslangic;
        String bit = (bitis == null || bitis.isBlank()) ? bas : bitis;
        String anahtar = MENU_ON_EK + bas + ":" + bit + ":" + (yemekhaneId == null || yemekhaneId <= 0 ? "tum" : yemekhaneId);

        long t0 = System.nanoTime();
        try {
            String cached = redis.opsForValue().get(anahtar);
            if (cached != null) {
                List<Map<String, Object>> liste = mapper.readValue(cached, new TypeReference<List<Map<String, Object>>>() {});
                double ms = Math.round(((System.nanoTime() - t0) / 1_000_000.0) * 100.0) / 100.0;
                return Map.of("veri", liste, "cache", "HIT", "sure_ms", ms);
            }
        } catch (Exception ignored) {
        }

        List<Map<String, Object>> sonuc = menuleriGetir(bas, bit, yemekhaneId);
        try {
            redis.opsForValue().set(anahtar, mapper.writeValueAsString(sonuc), MENU_TTL);
        } catch (Exception ignored) {
        }
        double ms = Math.round(((System.nanoTime() - t0) / 1_000_000.0) * 100.0) / 100.0;
        return Map.of("veri", sonuc, "cache", "MISS", "sure_ms", ms);
    }

    public Map<String, Object> rezervasyonYap(Long kullaniciId, Long menuId) {
        Map<String, Object> res = repo.spRezervasyonYap(kullaniciId, menuId);
        menuOnbellegiTemizle();
        return res;
    }

    public Map<String, Object> rezervasyonIptal(Long kullaniciId, Long rezervasyonId) {
        Map<String, Object> res = repo.spRezervasyonIptal(kullaniciId, rezervasyonId);
        menuOnbellegiTemizle();
        return res;
    }

    public Map<String, Object> bakiyeYukle(Long kullaniciId, BigDecimal tutar) {
        return repo.spBakiyeYukle(kullaniciId, tutar);
    }

    public Map<String, Object> turnikeGecis(Long kullaniciId, Integer yemekhaneId, String zamanStr) {
        String zaman = null;
        if (zamanStr != null && !zamanStr.isBlank()) {
            zaman = zamanStr.trim().replace("T", " ");
            if (zaman.length() == 16) {
                zaman += ":00";
            }
        }
        Map<String, Object> res = repo.spTurnikeGecis(kullaniciId, yemekhaneId, zaman);
        menuOnbellegiTemizle();
        return res;
    }

    public void degerlendirmeEkle(Long kullaniciId, Long menuId, int puan, String yorum) {
        if (puan < 1 || puan > 5) {
            throw new IsKuraliHatasi("Puan 1 ile 5 arasında olmalıdır.");
        }
        Integer sayi = repo.kullanilmisRezervasyonSayisi(kullaniciId, menuId);
        if (sayi == null || sayi == 0) {
            throw new IsKuraliHatasi("Yalnızca turnikeden geçerek kullandığınız menüleri değerlendirebilirsiniz.");
        }
        repo.degerlendirmeKaydet(kullaniciId, menuId, puan, yorum == null ? "" : yorum.trim());
        menuOnbellegiTemizle();
    }

    public Long yoneticiMenuEkle(Map<String, Object> body) {
        int yemekhaneId = ((Number) body.get("yemekhane_id")).intValue();
        String tarih = String.valueOf(body.get("tarih"));
        int ogunId = ((Number) body.get("ogun_id")).intValue();
        BigDecimal ogrFiyat = new BigDecimal(String.valueOf(body.get("ogrenci_fiyati")));
        BigDecimal perFiyat = new BigDecimal(String.valueOf(body.get("personel_fiyati")));
        List<?> hamListe = (List<?>) body.get("yemek_idler");
        if (hamListe == null || hamListe.isEmpty()) {
            throw new IsKuraliHatasi("Menü için en az bir yemek seçmelisiniz.");
        }
        List<Integer> yemekIdler = hamListe.stream().map(x -> ((Number) x).intValue()).toList();
        Long yeniId = repo.yeniMenuOlustur(yemekhaneId, tarih, ogunId, ogrFiyat, perFiyat, yemekIdler);
        menuOnbellegiTemizle();
        return yeniId;
    }

    public void yoneticiMenuGuncelle(Long menuId, Map<String, Object> body) {
        BigDecimal ogrFiyat = new BigDecimal(String.valueOf(body.get("ogrenci_fiyati")));
        BigDecimal perFiyat = new BigDecimal(String.valueOf(body.get("personel_fiyati")));
        List<?> hamListe = (List<?>) body.get("yemek_idler");
        List<Integer> yemekIdler = hamListe == null ? List.of() : hamListe.stream().map(x -> ((Number) x).intValue()).toList();
        repo.menuGuncelle(menuId, ogrFiyat, perFiyat, yemekIdler);
        menuOnbellegiTemizle();
    }

    public YemekhaneRepository getRepo() {
        return repo;
    }
}