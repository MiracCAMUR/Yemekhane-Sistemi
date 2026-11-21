package edu.kampus.yemekhane.controller;

import edu.kampus.yemekhane.exception.IsKuraliHatasi;
import edu.kampus.yemekhane.service.OturumServisi;
import edu.kampus.yemekhane.service.YemekhaneServisi;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class YemekhaneController {

    private static final String CEREZ_ADI = "YEMEKHANE_OTURUM";
    private final YemekhaneServisi servis;
    private final OturumServisi oturumServisi;

    public YemekhaneController(YemekhaneServisi servis, OturumServisi oturumServisi) {
        this.servis = servis;
        this.oturumServisi = oturumServisi;
    }

    private String cerezOku(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (CEREZ_ADI.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private Map<String, Object> zorunluKullanici(HttpServletRequest req) {
        Long id = oturumServisi.kullaniciIdGetir(cerezOku(req));
        if (id == null) {
            throw new IsKuraliHatasi("Oturum açmanız gerekiyor.", 401);
        }
        return servis.aktifKullaniciBilgisi(id);
    }

    private Map<String, Object> zorunluYonetici(HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        if (!"yonetici".equals(String.valueOf(k.get("rol")))) {
            throw new IsKuraliHatasi("Bu işlem için yönetici yetkisi gerekiyor.", 403);
        }
        return k;
    }

    @GetMapping({"", "/", "/saglik"})
    public Map<String, Object> saglik() {
        return Map.of("durum", "aktif", "servis", "yemekhane-api");
    }

    @PostMapping("/giris")
    public ResponseEntity<Map<String, Object>> giris(@RequestBody Map<String, String> body, HttpServletResponse res) {
        Map<String, Object> kullanici = servis.girisYap(body.get("eposta"), body.get("sifre"));
        Long id = ((Number) kullanici.get("id")).longValue();
        String token = oturumServisi.oturumOlustur(id);

        ResponseCookie cookie = ResponseCookie.from(CEREZ_ADI, token)
                .httpOnly(true)
                .path("/")
                .maxAge(86400)
                .sameSite("Lax")
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(kullanici);
    }

    @PostMapping("/cikis")
    public ResponseEntity<Map<String, String>> cikis(HttpServletRequest req, HttpServletResponse res) {
        oturumServisi.oturumSil(cerezOku(req));
        ResponseCookie cookie = ResponseCookie.from(CEREZ_ADI, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(Map.of("mesaj", "Çıkış yapıldı."));
    }

    @GetMapping("/ben")
    public Map<String, Object> ben(HttpServletRequest req) {
        return zorunluKullanici(req);
    }

    @GetMapping("/referanslar")
    public Map<String, Object> referanslar() {
        return Map.of(
                "yemekhaneler", servis.getRepo().yemekhaneleriGetir(),
                "ogunler", servis.getRepo().ogunleriGetir(),
                "yemekler", servis.getRepo().tumYemekleriGetir()
        );
    }

    @GetMapping("/menuler")
    public ResponseEntity<List<Map<String, Object>>> menuler(
            @RequestParam(required = false) String baslangic,
            @RequestParam(required = false) String bitis,
            @RequestParam(required = false) Integer yemekhaneId) {
        Map<String, Object> sonuc = servis.menulerOnbellekli(baslangic, bitis, yemekhaneId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> veri = (List<Map<String, Object>>) sonuc.get("veri");
        return ResponseEntity.ok()
                .header("X-Cache", String.valueOf(sonuc.get("cache")))
                .header("X-Response-Time-Ms", String.valueOf(sonuc.get("sure_ms")))
                .body(veri);
    }

    @GetMapping("/menuler/{menuId}/yorumlar")
    public List<Map<String, Object>> menuYorumlari(@PathVariable Long menuId) {
        return servis.getRepo().menuYorumlariniGetir(menuId);
    }

    @PostMapping("/rezervasyonlar")
    public Map<String, Object> rezervasyonYap(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        Long kullaniciId = ((Number) k.get("id")).longValue();
        Long menuId = ((Number) body.get("menu_id")).longValue();
        return servis.rezervasyonYap(kullaniciId, menuId);
    }

    @DeleteMapping("/rezervasyonlar/{id}")
    public Map<String, Object> rezervasyonIptal(@PathVariable Long id, HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        Long kullaniciId = ((Number) k.get("id")).longValue();
        return servis.rezervasyonIptal(kullaniciId, id);
    }

    @GetMapping("/rezervasyonlar")
    public List<Map<String, Object>> rezervasyonlarim(HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        return servis.getRepo().kullaniciRezervasyonlariniGetir(((Number) k.get("id")).longValue());
    }

    @PostMapping("/bakiye/yukle")
    public Map<String, Object> bakiyeYukle(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        Long kullaniciId = ((Number) k.get("id")).longValue();
        BigDecimal tutar = new BigDecimal(String.valueOf(body.get("tutar")));
        return servis.bakiyeYukle(kullaniciId, tutar);
    }

    @GetMapping("/bakiye/hareketler")
    public List<Map<String, Object>> bakiyeHareketleri(HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        return servis.getRepo().bakiyeHareketleriniGetir(((Number) k.get("id")).longValue());
    }

    @PostMapping("/degerlendirmeler")
    public Map<String, String> degerlendirmeEkle(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Map<String, Object> k = zorunluKullanici(req);
        Long kullaniciId = ((Number) k.get("id")).longValue();
        Long menuId = ((Number) body.get("menu_id")).longValue();
        int puan = ((Number) body.get("puan")).intValue();
        String yorum = body.get("yorum") != null ? String.valueOf(body.get("yorum")) : "";
        servis.degerlendirmeEkle(kullaniciId, menuId, puan, yorum);
        return Map.of("mesaj", "Değerlendirmeniz kaydedildi.");
    }

    @PostMapping("/yonetici/menuler")
    public Map<String, Object> yoneticiMenuEkle(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        zorunluYonetici(req);
        Long menuId = servis.yoneticiMenuEkle(body);
        return Map.of("menu_id", menuId, "mesaj", "Yeni menü başarıyla oluşturuldu.");
    }

    @PutMapping("/yonetici/menuler/{menuId}")
    public Map<String, String> yoneticiMenuGuncelle(@PathVariable Long menuId, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        zorunluYonetici(req);
        servis.yoneticiMenuGuncelle(menuId, body);
        return Map.of("mesaj", "Menü güncellendi.");
    }

    @GetMapping("/yonetici/rapor")
    public List<Map<String, Object>> yoneticiRapor(@RequestParam(required = false) String tarih, HttpServletRequest req) {
        zorunluYonetici(req);
        String t = (tarih == null || tarih.isBlank()) ? LocalDate.now().toString() : tarih;
        return servis.getRepo().yoneticiRaporGetir(t);
    }

    @GetMapping("/turnike/kullanicilar")
    public List<Map<String, Object>> turnikeKullanicilar(HttpServletRequest req) {
        zorunluKullanici(req);
        return servis.getRepo().tumKullanicilarOzet();
    }

    @PostMapping("/turnike/gecis")
    public Map<String, Object> turnikeGecis(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        zorunluKullanici(req);
        Long kullaniciId = ((Number) body.get("kullanici_id")).longValue();
        Integer yemekhaneId = ((Number) body.get("yemekhane_id")).intValue();
        String zaman = body.get("zaman") != null ? String.valueOf(body.get("zaman")) : null;
        return servis.turnikeGecis(kullaniciId, yemekhaneId, zaman);
    }
}