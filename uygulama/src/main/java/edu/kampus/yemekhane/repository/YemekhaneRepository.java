package edu.kampus.yemekhane.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Repository
public class YemekhaneRepository {

    private final JdbcTemplate yazma;
    private final JdbcTemplate okuma;

    public YemekhaneRepository(
            @Qualifier("yazmaJdbcTemplate") JdbcTemplate yazma,
            @Qualifier("okumaJdbcTemplate") JdbcTemplate okuma) {
        this.yazma = yazma;
        this.okuma = okuma;
    }

    public List<Map<String, Object>> kullaniciBulEpostaIle(String eposta) {
        return okuma.queryForList(
                "SELECT id, ad, soyad, eposta, sifre_hash, rol, ogrenci_no, bakiye, aktif FROM kullanicilar WHERE eposta = ?",
                eposta);
    }

    public List<Map<String, Object>> kullaniciBulIdIleKaynaktan(Long id) {
        return yazma.queryForList(
                "SELECT id, ad, soyad, eposta, rol, ogrenci_no, bakiye, aktif FROM kullanicilar WHERE id = ?",
                id);
    }

    public List<Map<String, Object>> tumKullanicilarOzet() {
        return okuma.queryForList(
                "SELECT id, ad, soyad, eposta, rol, ogrenci_no, bakiye FROM kullanicilar WHERE aktif = 1 ORDER BY id");
    }

    public List<Map<String, Object>> tumAlerjenleriGetir() {
        return okuma.queryForList("SELECT id, ad FROM alerjenler ORDER BY id");
    }

    public List<Integer> kullaniciAlerjenIdleriGetir(Long kullaniciId) {
        return yazma.queryForList(
                "SELECT alerjen_id FROM kullanici_alerjenleri WHERE kullanici_id = ? ORDER BY alerjen_id",
                Integer.class,
                kullaniciId);
    }

    public void kullaniciAlerjenleriKaydet(Long kullaniciId, List<Integer> alerjenIdler) {
        yazma.update("DELETE FROM kullanici_alerjenleri WHERE kullanici_id = ?", kullaniciId);
        if (alerjenIdler != null) {
            for (Integer alerjenId : alerjenIdler) {
                yazma.update(
                        "INSERT IGNORE INTO kullanici_alerjenleri (kullanici_id, alerjen_id) VALUES (?, ?)",
                        kullaniciId, alerjenId);
            }
        }
    }

    public List<Map<String, Object>> yemekhaneleriGetir() {
        return okuma.queryForList("SELECT id, ad, konum, kapasite FROM yemekhaneler ORDER BY id");
    }

    public List<Map<String, Object>> ogunleriGetir() {
        return okuma.queryForList("SELECT id, ad, sira, son_saat, servis_baslangic, servis_bitis FROM ogunler ORDER BY sira");
    }

    public List<Map<String, Object>> tumYemekleriGetir() {
        return okuma.queryForList(
                "SELECT y.id, y.ad, y.kalori, y.vejetaryen, k.ad AS kategori, k.sira AS kategori_sira " +
                "FROM yemekler y JOIN yemek_kategorileri k ON k.id = y.kategori_id ORDER BY k.sira, y.ad");
    }

    public List<Map<String, Object>> tarihAraligiMenuleriGetir(String baslangic, String bitis, Integer yemekhaneId) {
        StringBuilder sql = new StringBuilder(
                "SELECT d.menu_id, d.tarih, d.yemekhane_id, d.yemekhane, d.ogun_id, d.ogun, d.rezervasyon_son, " +
                "m.ogrenci_fiyati, m.personel_fiyati, d.kapasite, d.rezerve, d.bos_yer, d.doluluk_yuzde, " +
                "COALESCE(p.oy_sayisi, 0) AS oy_sayisi, p.ortalama_puan " +
                "FROM v_menu_doluluk d " +
                "JOIN menuler m ON m.id = d.menu_id " +
                "JOIN ogunler o ON o.id = d.ogun_id " +
                "LEFT JOIN v_menu_puan p ON p.menu_id = d.menu_id " +
                "WHERE d.tarih BETWEEN ? AND ? ");
        if (yemekhaneId != null && yemekhaneId > 0) {
            sql.append("AND d.yemekhane_id = ? ");
            sql.append("ORDER BY d.tarih, o.sira, d.yemekhane_id");
            return okuma.queryForList(sql.toString(), baslangic, bitis, yemekhaneId);
        }
        sql.append("ORDER BY d.tarih, o.sira, d.yemekhane_id");
        return okuma.queryForList(sql.toString(), baslangic, bitis);
    }

    public List<Map<String, Object>> menuYemekleriniGetir(List<Long> menuIds) {
        if (menuIds.isEmpty()) return List.of();
        String placeholders = String.join(",", menuIds.stream().map(id -> "?").toList());
        String sql = "SELECT menu_id, yemek_id, yemek, kategori, kategori_sira, kalori, vejetaryen, alerjenler, alerjen_idler " +
                     "FROM v_menu_detay WHERE menu_id IN (" + placeholders + ") " +
                     "ORDER BY menu_id, kategori_sira, yemek";
        return okuma.queryForList(sql, menuIds.toArray());
    }

    public List<Map<String, Object>> menuYorumlariniGetir(Long menuId) {
        return okuma.queryForList(
                "SELECT d.id, d.puan, d.yorum, d.olusturma, CONCAT(k.ad, ' ', LEFT(k.soyad, 1), '.') AS kullanici " +
                "FROM degerlendirmeler d JOIN kullanicilar k ON k.id = d.kullanici_id " +
                "WHERE d.menu_id = ? ORDER BY d.olusturma DESC",
                menuId);
    }

    public Map<String, Object> spRezervasyonYap(Long kullaniciId, Long menuId, String porsiyon) {
        String secilenPorsiyon = (porsiyon == null || porsiyon.isBlank()) ? "tam" : porsiyon;
        return yazma.queryForMap("CALL sp_rezervasyon_yap(?, ?, ?)", kullaniciId, menuId, secilenPorsiyon);
    }

    public Map<String, Object> spRezervasyonIptal(Long kullaniciId, Long rezervasyonId) {
        return yazma.queryForMap("CALL sp_rezervasyon_iptal(?, ?)", kullaniciId, rezervasyonId);
    }

    public Map<String, Object> spBakiyeYukle(Long kullaniciId, BigDecimal tutar) {
        return yazma.queryForMap("CALL sp_bakiye_yukle(?, ?)", kullaniciId, tutar);
    }

    public Map<String, Object> spTurnikeGecis(Long kullaniciId, Integer yemekhaneId, String zaman) {
        return yazma.queryForMap("CALL sp_turnike_gecis(?, ?, ?)", kullaniciId, yemekhaneId, zaman);
    }

    public List<Map<String, Object>> kullaniciRezervasyonlariniGetir(Long kullaniciId) {
        return okuma.queryForList(
                "SELECT r.id, r.menu_id, r.porsiyon, r.durum, r.ucret, r.olusturma, r.kullanma_zamani, r.iptal_zamani, " +
                "m.tarih, yh.ad AS yemekhane, o.ad AS ogun, TIMESTAMP(m.tarih, o.son_saat) AS rezervasyon_son, " +
                "(SELECT puan FROM degerlendirmeler d WHERE d.kullanici_id = r.kullanici_id AND d.menu_id = r.menu_id) AS verilen_puan " +
                "FROM rezervasyonlar r " +
                "JOIN menuler m ON m.id = r.menu_id " +
                "JOIN yemekhaneler yh ON yh.id = m.yemekhane_id " +
                "JOIN ogunler o ON o.id = m.ogun_id " +
                "WHERE r.kullanici_id = ? " +
                "ORDER BY m.tarih DESC, o.sira DESC",
                kullaniciId);
    }

    public List<Map<String, Object>> bakiyeHareketleriniGetir(Long kullaniciId) {
        return okuma.queryForList(
                "SELECT id, tur, tutar, rezervasyon_id, aciklama, olusturma " +
                "FROM bakiye_hareketleri WHERE kullanici_id = ? ORDER BY olusturma DESC, id DESC",
                kullaniciId);
    }

    public Integer kullanilmisRezervasyonSayisi(Long kullaniciId, Long menuId) {
        return yazma.queryForObject(
                "SELECT COUNT(*) FROM rezervasyonlar WHERE kullanici_id = ? AND menu_id = ? AND durum = 'kullanildi'",
                Integer.class, kullaniciId, menuId);
    }

    public void degerlendirmeKaydet(Long kullaniciId, Long menuId, int puan, String yorum) {
        yazma.update(
                "INSERT INTO degerlendirmeler (kullanici_id, menu_id, puan, yorum) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE puan = VALUES(puan), yorum = VALUES(yorum), olusturma = NOW()",
                kullaniciId, menuId, puan, yorum);
    }

    public Long yeniMenuOlustur(int yemekhaneId, String tarih, int ogunId, BigDecimal ogrFiyat, BigDecimal perFiyat, List<Integer> yemekIdler) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        yazma.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO menuler (yemekhane_id, tarih, ogun_id, ogrenci_fiyati, personel_fiyati) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, yemekhaneId);
            ps.setString(2, tarih);
            ps.setInt(3, ogunId);
            ps.setBigDecimal(4, ogrFiyat);
            ps.setBigDecimal(5, perFiyat);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long menuId = key != null ? key.longValue() : 0L;
        for (Integer yId : yemekIdler) {
            yazma.update("INSERT INTO menu_yemekleri (menu_id, yemek_id) VALUES (?, ?)", menuId, yId);
        }
        return menuId;
    }

    public void menuGuncelle(Long menuId, BigDecimal ogrFiyat, BigDecimal perFiyat, List<Integer> yemekIdler) {
        yazma.update("UPDATE menuler SET ogrenci_fiyati = ?, personel_fiyati = ? WHERE id = ?", ogrFiyat, perFiyat, menuId);
        if (yemekIdler != null && !yemekIdler.isEmpty()) {
            yazma.update("DELETE FROM menu_yemekleri WHERE menu_id = ?", menuId);
            for (Integer yId : yemekIdler) {
                yazma.update("INSERT INTO menu_yemekleri (menu_id, yemek_id) VALUES (?, ?)", menuId, yId);
            }
        }
    }

    public List<Map<String, Object>> yoneticiRaporGetir(String tarih) {
        return okuma.queryForList(
                "SELECT d.menu_id, d.tarih, d.yemekhane, d.ogun, d.kapasite, d.rezerve, d.bos_yer, d.doluluk_yuzde, " +
                "(SELECT COUNT(*) FROM rezervasyonlar r WHERE r.menu_id = d.menu_id AND r.durum = 'kullanildi') AS kullanilan, " +
                "COALESCE(p.oy_sayisi, 0) AS oy_sayisi, p.ortalama_puan " +
                "FROM v_menu_doluluk d LEFT JOIN v_menu_puan p ON p.menu_id = d.menu_id " +
                "WHERE d.tarih = ? ORDER BY d.yemekhane_id, d.ogun_id",
                tarih);
    }
}