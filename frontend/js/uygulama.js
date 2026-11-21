let aktifKullanici = null;
let referansVeri = { yemekhaneler: [], ogunler: [], yemekler: [] };
let turnikeLoglar = [];

const bugunStr = () => new Date().toISOString().slice(0, 10);
const gunEkle = (tarihStr, gun) => {
  const d = new Date(tarihStr + 'T12:00:00');
  d.setDate(d.getDate() + gun);
  return d.toISOString().slice(0, 10);
};
const paraFormat = (tutar) => Number(tutar || 0).toFixed(2).replace('.', ',') + ' TL';

function bildirimGoster(mesaj, tur = 'basari') {
  const alan = document.getElementById('global-bildirim');
  alan.textContent = '';
  if (!mesaj) return;
  const p = document.createElement('p');
  p.className = 'bildirim ' + (tur === 'hata' ? 'bildirim-hata' : 'bildirim-basari');
  p.setAttribute('role', tur === 'hata' ? 'alert' : 'status');
  p.textContent = mesaj;
  alan.appendChild(p);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function apiIstekDetayli(yol, secenekler = {}) {
  const ayar = {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    ...secenekler
  };
  const yanit = await fetch('/api' + yol, ayar);
  const veri = await yanit.json().catch(() => ({}));
  if (!yanit.ok) {
    throw new Error(veri.hata || ('İstek başarısız (HTTP ' + yanit.status + ')'));
  }
  return {
    veri,
    cache: yanit.headers.get('X-Cache'),
    sureMs: yanit.headers.get('X-Response-Time-Ms')
  };
}

async function apiIstek(yol, secenekler = {}) {
  const sonuc = await apiIstekDetayli(yol, secenekler);
  return sonuc.veri;
}

function cacheRozetGuncelle(elementId, cacheDurum, sureMs) {
  const el = document.getElementById(elementId);
  if (!el) return;
  if (!cacheDurum) {
    el.textContent = '';
    return;
  }
  const hit = cacheDurum === 'HIT';
  el.innerHTML = '';
  const rozet = document.createElement('span');
  rozet.className = 'etiket';
  rozet.style.fontWeight = '700';
  rozet.style.background = hit ? '#dcfce7' : '#fef3c7';
  rozet.style.color = hit ? '#166534' : '#92400e';
  rozet.textContent = 'Redis: ' + cacheDurum + (sureMs ? ' (' + sureMs + ' ms)' : '');
  el.appendChild(rozet);
}

function kullaniciUstCubukGuncelle() {
  const ust = document.getElementById('ust-kullanici');
  if (!aktifKullanici) {
    ust.classList.add('gizli');
    return;
  }
  ust.classList.remove('gizli');
  document.getElementById('ust-ad').textContent = aktifKullanici.ad + ' ' + aktifKullanici.soyad;
  document.getElementById('ust-rol').textContent = aktifKullanici.rol;
  document.getElementById('ust-bakiye').textContent = paraFormat(aktifKullanici.bakiye);

  const yoneticiBtn = document.getElementById('sekme-yonetici-btn');
  if (aktifKullanici.rol === 'yonetici') {
    yoneticiBtn.classList.remove('gizli');
  } else {
    yoneticiBtn.classList.add('gizli');
  }
}

async function referanslariYukle() {
  referansVeri = await apiIstek('/referanslar');
  const seciciler = ['filtre-yemekhane', 'hafta-yemekhane'];
  seciciler.forEach(id => {
    const el = document.getElementById(id);
    el.innerHTML = '<option value="0">Tüm Yemekhaneler</option>';
    referansVeri.yemekhaneler.forEach(y => {
      const opt = document.createElement('option');
      opt.value = y.id;
      opt.textContent = y.ad + ' (Kapasite: ' + y.kapasite + ')';
      el.appendChild(opt);
    });
  });

  ['ym-yemekhane', 'turnike-yemekhane'].forEach(id => {
    const el = document.getElementById(id);
    el.innerHTML = '';
    referansVeri.yemekhaneler.forEach(y => {
      const opt = document.createElement('option');
      opt.value = y.id;
      opt.textContent = y.ad;
      el.appendChild(opt);
    });
  });

  const ymOgun = document.getElementById('ym-ogun');
  ymOgun.innerHTML = '';
  referansVeri.ogunler.forEach(o => {
    const opt = document.createElement('option');
    opt.value = o.id;
    opt.textContent = o.ad + ' (Son: ' + String(o.son_saat).slice(0, 5) + ')';
    ymOgun.appendChild(opt);
  });

  const yemekKutusu = document.getElementById('ym-yemek-listesi');
  yemekKutusu.innerHTML = '';
  referansVeri.yemekler.forEach(y => {
    const lbl = document.createElement('label');
    lbl.className = 'yemek-secim-satir';
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.value = y.id;
    lbl.appendChild(cb);
    lbl.appendChild(document.createTextNode(' [' + y.kategori + '] ' + y.ad + ' (' + y.kalori + ' kcal)'));
    yemekKutusu.appendChild(lbl);
  });
}

function menuKartiOlustur(m, rezervasyonAktif = true) {
  const kart = document.createElement('article');
  kart.className = 'kart';

  const baslik = document.createElement('div');
  baslik.className = 'menu-baslik';

  const sol = document.createElement('div');
  const h3 = document.createElement('h3');
  h3.style.marginBottom = '0.15rem';
  h3.textContent = m.yemekhane + ' · ' + m.ogun;
  const alt = document.createElement('div');
  alt.style.fontSize = 'var(--boyut-kucuk)';
  alt.style.color = 'var(--metin-soluk)';
  alt.textContent = 'Tarih: ' + m.tarih + ' | Son Saat: ' + String(m.rezervasyon_son).slice(11, 16);
  sol.appendChild(h3);
  sol.appendChild(alt);

  const sag = document.createElement('div');
  sag.style.textAlign = 'right';
  const ucret = aktifKullanici.rol === 'ogrenci' ? m.ogrenci_fiyati : m.personel_fiyati;
  const fiyatRozet = document.createElement('div');
  fiyatRozet.style.fontWeight = '700';
  fiyatRozet.style.color = 'var(--ana)';
  fiyatRozet.textContent = paraFormat(ucret);
  const kaloriBilgi = document.createElement('div');
  kaloriBilgi.style.fontSize = 'var(--boyut-kucuk)';
  kaloriBilgi.style.color = 'var(--metin-soluk)';
  kaloriBilgi.textContent = 'Toplam: ' + m.toplam_kalori + ' kcal';
  sag.appendChild(fiyatRozet);
  sag.appendChild(kaloriBilgi);

  baslik.appendChild(sol);
  baslik.appendChild(sag);
  kart.appendChild(baslik);

  const ul = document.createElement('ul');
  ul.className = 'yemek-listesi';
  (m.yemekler || []).forEach(y => {
    const li = document.createElement('li');
    const adSpan = document.createElement('span');
    adSpan.textContent = y.yemek + ' ';
    const katSpan = document.createElement('span');
    katSpan.className = 'etiket';
    katSpan.textContent = y.kategori;
    adSpan.appendChild(katSpan);

    if (Number(y.vejetaryen) === 1) {
      const vej = document.createElement('span');
      vej.className = 'etiket etiket-vejetaryen';
      vej.textContent = 'Vejetaryen';
      adSpan.appendChild(vej);
    }
    if (y.alerjenler) {
      const alr = document.createElement('span');
      alr.className = 'etiket etiket-alerjen';
      alr.textContent = 'Alerjen: ' + y.alerjenler;
      adSpan.appendChild(alr);
    }

    const kcalSpan = document.createElement('span');
    kcalSpan.style.fontSize = 'var(--boyut-kucuk)';
    kcalSpan.style.color = 'var(--metin-soluk)';
    kcalSpan.textContent = y.kalori + ' kcal';

    li.appendChild(adSpan);
    li.appendChild(kcalSpan);
    ul.appendChild(li);
  });
  kart.appendChild(ul);

  const dolulukMetin = document.createElement('div');
  dolulukMetin.style.fontSize = 'var(--boyut-kucuk)';
  dolulukMetin.style.display = 'flex';
  dolulukMetin.style.justifyContent = 'space-between';
  const puanMetin = m.ortalama_puan ? ('★ ' + m.ortalama_puan + ' (' + m.oy_sayisi + ' oy)') : 'Henüz puanlanmadı';
  dolulukMetin.textContent = 'Doluluk: ' + m.rezerve + '/' + m.kapasite + ' (Boş: ' + m.bos_yer + ') · ' + puanMetin;
  kart.appendChild(dolulukMetin);

  const cubuk = document.createElement('div');
  cubuk.className = 'doluluk-cubuk';
  const ic = document.createElement('div');
  ic.className = 'doluluk-ic';
  ic.style.width = Math.min(100, Number(m.doluluk_yuzde || 0)) + '%';
  cubuk.appendChild(ic);
  kart.appendChild(cubuk);

  const altButonlar = document.createElement('div');
  altButonlar.style.display = 'flex';
  altButonlar.style.gap = '0.5rem';
  altButonlar.style.marginTop = '0.75rem';
  altButonlar.style.flexWrap = 'wrap';

  if (rezervasyonAktif) {
    const rezBtn = document.createElement('button');
    rezBtn.type = 'button';
    rezBtn.className = 'dugme dugme-kucuk';
    const sureDoldu = new Date(String(m.rezervasyon_son).replace(' ', 'T')) <= new Date();
    const yerYok = Number(m.bos_yer) <= 0;
    if (sureDoldu) {
      rezBtn.disabled = true;
      rezBtn.textContent = 'Rezervasyon Süresi Doldu';
    } else if (yerYok) {
      rezBtn.disabled = true;
      rezBtn.textContent = 'Kapasite Dolu';
    } else {
      rezBtn.textContent = 'Rezervasyon Yap (' + paraFormat(ucret) + ')';
      rezBtn.addEventListener('click', async () => {
        try {
          const sonuc = await apiIstek('/rezervasyonlar', {
            method: 'POST',
            body: JSON.stringify({ menu_id: m.menu_id })
          });
          aktifKullanici.bakiye = sonuc.bakiye;
          kullaniciUstCubukGuncelle();
          bildirimGoster('Rezervasyon onaylandı! Kalan bakiye: ' + paraFormat(sonuc.bakiye), 'basari');
          gunlukMenuleriYukle();
        } catch (err) {
          bildirimGoster(err.message, 'hata');
        }
      });
    }
    altButonlar.appendChild(rezBtn);
  }

  const yorumBtn = document.createElement('button');
  yorumBtn.type = 'button';
  yorumBtn.className = 'dugme dugme-kucuk';
  yorumBtn.style.background = 'var(--metin-soluk)';
  yorumBtn.textContent = 'Yorumları Gör (' + (m.oy_sayisi || 0) + ')';
  const yorumAlani = document.createElement('div');
  yorumAlani.className = 'gizli';
  yorumAlani.style.marginTop = '0.6rem';
  yorumAlani.style.fontSize = 'var(--boyut-kucuk)';

  yorumBtn.addEventListener('click', async () => {
    if (!yorumAlani.classList.contains('gizli')) {
      yorumAlani.classList.add('gizli');
      return;
    }
    const yorumlar = await apiIstek('/menuler/' + m.menu_id + '/yorumlar');
    yorumAlani.innerHTML = '';
    if (yorumlar.length === 0) {
      yorumAlani.textContent = 'Bu menü için henüz yorum yapılmamış.';
    } else {
      yorumlar.forEach(y => {
        const satir = document.createElement('div');
        satir.style.padding = '0.35rem 0';
        satir.style.borderTop = '1px solid var(--cizgi)';
        satir.textContent = '★ ' + y.puan + '/5 — ' + y.kullanici + ': ' + (y.yorum || '(Yorum yok)');
        yorumAlani.appendChild(satir);
      });
    }
    yorumAlani.classList.remove('gizli');
  });

  altButonlar.appendChild(yorumBtn);
  kart.appendChild(altButonlar);
  kart.appendChild(yorumAlani);
  return kart;
}

async function gunlukMenuleriYukle() {
  const tarih = document.getElementById('filtre-tarih').value || bugunStr();
  const yh = document.getElementById('filtre-yemekhane').value;
  const yanit = await apiIstekDetayli('/menuler?baslangic=' + tarih + '&bitis=' + tarih + '&yemekhaneId=' + yh);
  cacheRozetGuncelle('gunluk-cache-bilgi', yanit.cache, yanit.sureMs);
  const menuler = yanit.veri;
  const liste = document.getElementById('gunluk-liste');
  liste.innerHTML = '';
  if (menuler.length === 0) {
    liste.innerHTML = '<p class="kart">Seçilen tarih ve yemekhane için tanımlı menü bulunamadı.</p>';
    return;
  }
  menuler.forEach(m => liste.appendChild(menuKartiOlustur(m, true)));
}

async function haftalikMenuleriYukle() {
  const bas = document.getElementById('hafta-baslangic').value || bugunStr();
  const bit = gunEkle(bas, 6);
  const yh = document.getElementById('hafta-yemekhane').value;
  const yanit = await apiIstekDetayli('/menuler?baslangic=' + bas + '&bitis=' + bit + '&yemekhaneId=' + yh);
  cacheRozetGuncelle('haftalik-cache-bilgi', yanit.cache, yanit.sureMs);
  const menuler = yanit.veri;
  const liste = document.getElementById('haftalik-liste');
  liste.innerHTML = '';
  if (menuler.length === 0) {
    liste.innerHTML = '<p class="kart">Bu 7 günlük aralıkta tanımlı menü bulunamadı.</p>';
    return;
  }
  menuler.forEach(m => liste.appendChild(menuKartiOlustur(m, true)));
}

async function rezervasyonlariYukle() {
  const liste = await apiIstek('/rezervasyonlar');
  const tbody = document.getElementById('rezervasyon-tablo');
  tbody.innerHTML = '';
  if (liste.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5">Henüz rezervasyonunuz bulunmuyor.</td></tr>';
    return;
  }
  liste.forEach(r => {
    const tr = document.createElement('tr');
    const td1 = document.createElement('td');
    td1.textContent = r.tarih + ' · ' + r.ogun;
    const td2 = document.createElement('td');
    td2.textContent = r.yemekhane;
    const td3 = document.createElement('td');
    td3.textContent = paraFormat(r.ucret);
    const td4 = document.createElement('td');
    td4.textContent = r.durum.toUpperCase();
    const td5 = document.createElement('td');

    if (r.durum === 'aktif') {
      const iptalBtn = document.createElement('button');
      iptalBtn.type = 'button';
      iptalBtn.className = 'dugme dugme-tehlike dugme-kucuk';
      iptalBtn.textContent = 'İptal Et';
      iptalBtn.addEventListener('click', async () => {
        try {
          const sonuc = await apiIstek('/rezervasyonlar/' + r.id, { method: 'DELETE' });
          aktifKullanici.bakiye = sonuc.bakiye;
          kullaniciUstCubukGuncelle();
          bildirimGoster('Rezervasyon iptal edildi, ' + paraFormat(sonuc.iade) + ' iade edildi.', 'basari');
          rezervasyonlariYukle();
        } catch (err) {
          bildirimGoster(err.message, 'hata');
        }
      });
      td5.appendChild(iptalBtn);
    } else if (r.durum === 'kullanildi') {
      const degBtn = document.createElement('button');
      degBtn.type = 'button';
      degBtn.className = 'dugme dugme-kucuk';
      degBtn.textContent = r.verilen_puan ? ('Puanın: ★' + r.verilen_puan + ' (Düzenle)') : 'Değerlendir';
      degBtn.addEventListener('click', () => {
        document.getElementById('deg-menu-id').value = r.menu_id;
        document.getElementById('deg-baslik').textContent = r.tarih + ' ' + r.ogun + ' (' + r.yemekhane + ') Değerlendir';
        document.getElementById('degerlendirme-kutu').classList.remove('gizli');
        document.getElementById('degerlendirme-kutu').scrollIntoView({ behavior: 'smooth' });
      });
      td5.appendChild(degBtn);
    } else {
      td5.textContent = '—';
    }

    tr.append(td1, td2, td3, td4, td5);
    tbody.appendChild(tr);
  });
}

async function bakiyeHareketleriniYukle() {
  const liste = await apiIstek('/bakiye/hareketler');
  const tbody = document.getElementById('hareket-tablo');
  tbody.innerHTML = '';
  if (liste.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4">Bakiye hareketi yok.</td></tr>';
    return;
  }
  liste.forEach(h => {
    const tr = document.createElement('tr');
    const td1 = document.createElement('td');
    td1.textContent = String(h.olusturma).replace('T', ' ').slice(0, 16);
    const td2 = document.createElement('td');
    td2.textContent = h.tur.toUpperCase();
    const td3 = document.createElement('td');
    td3.textContent = h.aciklama || '';
    const td4 = document.createElement('td');
    const tutar = Number(h.tutar);
    td4.style.fontWeight = '700';
    td4.style.color = tutar >= 0 ? 'var(--basari)' : 'var(--hata)';
    td4.textContent = (tutar >= 0 ? '+' : '') + paraFormat(tutar);
    tr.append(td1, td2, td3, td4);
    tbody.appendChild(tr);
  });
}

async function turnikeKullanicilariniYukle() {
  const liste = await apiIstek('/turnike/kullanicilar');
  const sec = document.getElementById('turnike-kullanici');
  const oncekiSecim = sec.value;
  sec.innerHTML = '';
  liste.forEach(k => {
    const opt = document.createElement('option');
    opt.value = k.id;
    const no = k.ogrenci_no ? (' · ' + k.ogrenci_no) : '';
    opt.textContent = k.ad + ' ' + k.soyad + ' (' + k.rol + no + ') - Bakiye: ' + paraFormat(k.bakiye);
    sec.appendChild(opt);
  });
  if (oncekiSecim) {
    sec.value = oncekiSecim;
  } else if (aktifKullanici) {
    sec.value = String(aktifKullanici.id);
  }
}

function turnikeLogTabloGuncelle() {
  const tbody = document.getElementById('turnike-log-tablo');
  tbody.innerHTML = '';
  if (turnikeLoglar.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4">Henüz turnike geçişi yapılmadı.</td></tr>';
    return;
  }
  turnikeLoglar.forEach(kayit => {
    const tr = document.createElement('tr');
    const td1 = document.createElement('td');
    td1.textContent = kayit.zaman;
    const td2 = document.createElement('td');
    td2.textContent = kayit.kullanici;
    const td3 = document.createElement('td');
    td3.textContent = kayit.yemekhane;
    const td4 = document.createElement('td');
    td4.style.fontWeight = '700';
    td4.style.color = kayit.basarili ? 'var(--basari)' : 'var(--hata)';
    td4.textContent = kayit.mesaj;
    tr.append(td1, td2, td3, td4);
    tbody.appendChild(tr);
  });
}

async function yoneticiRaporYukle() {
  const tarih = document.getElementById('rapor-tarih').value || bugunStr();
  const liste = await apiIstek('/yonetici/rapor?tarih=' + tarih);
  const tbody = document.getElementById('rapor-tablo');
  tbody.innerHTML = '';
  if (liste.length === 0) {
    tbody.innerHTML = '<tr><td colspan="4">Bu tarih için menü kaydı yok.</td></tr>';
    return;
  }
  liste.forEach(r => {
    const tr = document.createElement('tr');
    const td1 = document.createElement('td');
    td1.textContent = r.yemekhane + ' · ' + r.ogun;
    const td2 = document.createElement('td');
    td2.textContent = r.rezerve + '/' + r.kapasite + ' (%' + r.doluluk_yuzde + ')';
    const td3 = document.createElement('td');
    td3.textContent = r.kullanilan + ' kişi';
    const td4 = document.createElement('td');
    td4.textContent = r.ortalama_puan ? ('★ ' + r.ortalama_puan + ' (' + r.oy_sayisi + ')') : '—';
    tr.append(td1, td2, td3, td4);
    tbody.appendChild(tr);
  });
}

async function oturumBaslat() {
  document.getElementById('ekran-giris').classList.add('gizli');
  document.getElementById('ana-panel').classList.remove('gizli');
  kullaniciUstCubukGuncelle();
  await referanslariYukle();
  const bugun = bugunStr();
  document.getElementById('filtre-tarih').value = bugun;
  document.getElementById('hafta-baslangic').value = bugun;
  document.getElementById('ym-tarih').value = gunEkle(bugun, 1);
  document.getElementById('rapor-tarih').value = bugun;
  await gunlukMenuleriYukle();
}

document.addEventListener('DOMContentLoaded', async () => {
  document.querySelectorAll('[data-hizli]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.getElementById('giris-eposta').value = btn.dataset.hizli;
      document.getElementById('giris-sifre').value = 'Yemek2026!';
    });
  });

  document.getElementById('form-giris').addEventListener('submit', async (e) => {
    e.preventDefault();
    bildirimGoster('');
    try {
      aktifKullanici = await apiIstek('/giris', {
        method: 'POST',
        body: JSON.stringify({
          eposta: document.getElementById('giris-eposta').value,
          sifre: document.getElementById('giris-sifre').value
        })
      });
      await oturumBaslat();
    } catch (err) {
      bildirimGoster(err.message, 'hata');
    }
  });

  document.getElementById('btn-cikis').addEventListener('click', async () => {
    await apiIstek('/cikis', { method: 'POST' }).catch(() => {});
    aktifKullanici = null;
    kullaniciUstCubukGuncelle();
    document.getElementById('ana-panel').classList.add('gizli');
    document.getElementById('ekran-giris').classList.remove('gizli');
    bildirimGoster('Oturum kapatıldı.', 'basari');
  });

  document.querySelectorAll('.sekme-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      bildirimGoster('');
      document.querySelectorAll('.sekme-btn').forEach(b => b.classList.remove('aktif'));
      btn.classList.add('aktif');
      const hedef = btn.dataset.sekme;
      ['gunluk', 'haftalik', 'rezervasyonlar', 'bakiye', 'turnike', 'yonetici'].forEach(p => {
        document.getElementById('panel-' + p).classList.toggle('gizli', p !== hedef);
      });
      if (hedef === 'gunluk') await gunlukMenuleriYukle();
      if (hedef === 'haftalik') await haftalikMenuleriYukle();
      if (hedef === 'rezervasyonlar') await rezervasyonlariYukle();
      if (hedef === 'bakiye') await bakiyeHareketleriniYukle();
      if (hedef === 'turnike') await turnikeKullanicilariniYukle();
      if (hedef === 'yonetici') await yoneticiRaporYukle();
    });
  });

  document.getElementById('btn-gunluk-getir').addEventListener('click', gunlukMenuleriYukle);
  document.getElementById('btn-haftalik-getir').addEventListener('click', haftalikMenuleriYukle);
  document.getElementById('btn-rapor-getir').addEventListener('click', yoneticiRaporYukle);

  document.getElementById('btn-zaman-simdi').addEventListener('click', () => {
    document.getElementById('turnike-zaman').value = '';
  });
  document.getElementById('btn-zaman-ogle').addEventListener('click', () => {
    document.getElementById('turnike-zaman').value = bugunStr() + 'T12:15';
  });
  document.getElementById('btn-zaman-aksam').addEventListener('click', () => {
    document.getElementById('turnike-zaman').value = bugunStr() + 'T18:15';
  });

  document.getElementById('form-turnike').addEventListener('submit', async (e) => {
    e.preventDefault();
    const kullaniciSel = document.getElementById('turnike-kullanici');
    const yemekhaneSel = document.getElementById('turnike-yemekhane');
    const zamanDeger = document.getElementById('turnike-zaman').value;
    const kullaniciAd = kullaniciSel.options[kullaniciSel.selectedIndex]?.text.split(' - Bakiye')[0] || '';
    const yemekhaneAd = yemekhaneSel.options[yemekhaneSel.selectedIndex]?.text || '';
    const gosterimZaman = zamanDeger ? zamanDeger.replace('T', ' ') : 'NOW() (Anlık)';

    try {
      const sonuc = await apiIstek('/turnike/gecis', {
        method: 'POST',
        body: JSON.stringify({
          kullanici_id: Number(kullaniciSel.value),
          yemekhane_id: Number(yemekhaneSel.value),
          zaman: zamanDeger || null
        })
      });
      const ozet = 'Onaylandı · Öğün: ' + sonuc.ogun + ' (Rez #' + sonuc.rezervasyon_id + ')';
      bildirimGoster('Turnike geçişi başarılı! ' + ozet, 'basari');
      turnikeLoglar.unshift({ zaman: gosterimZaman, kullanici: kullaniciAd, yemekhane: yemekhaneAd, basarili: true, mesaj: ozet });
      turnikeLogTabloGuncelle();
    } catch (err) {
      bildirimGoster(err.message, 'hata');
      turnikeLoglar.unshift({ zaman: gosterimZaman, kullanici: kullaniciAd, yemekhane: yemekhaneAd, basarili: false, mesaj: err.message });
      turnikeLogTabloGuncelle();
    }
  });

  document.querySelectorAll('[data-tutar]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.getElementById('bakiye-tutar').value = btn.dataset.tutar;
    });
  });

  document.getElementById('form-bakiye').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const tutar = document.getElementById('bakiye-tutar').value;
      const sonuc = await apiIstek('/bakiye/yukle', {
        method: 'POST',
        body: JSON.stringify({ tutar })
      });
      aktifKullanici.bakiye = sonuc.bakiye;
      kullaniciUstCubukGuncelle();
      bildirimGoster('Bakiye yüklendi! Yeni bakiye: ' + paraFormat(sonuc.bakiye), 'basari');
      await bakiyeHareketleriniYukle();
    } catch (err) {
      bildirimGoster(err.message, 'hata');
    }
  });

  document.getElementById('form-degerlendirme').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await apiIstek('/degerlendirmeler', {
        method: 'POST',
        body: JSON.stringify({
          menu_id: Number(document.getElementById('deg-menu-id').value),
          puan: Number(document.getElementById('deg-puan').value),
          yorum: document.getElementById('deg-yorum').value
        })
      });
      document.getElementById('degerlendirme-kutu').classList.add('gizli');
      document.getElementById('deg-yorum').value = '';
      bildirimGoster('Değerlendirmeniz başarıyla kaydedildi.', 'basari');
      await rezervasyonlariYukle();
    } catch (err) {
      bildirimGoster(err.message, 'hata');
    }
  });

  document.getElementById('btn-deg-vazgec').addEventListener('click', () => {
    document.getElementById('degerlendirme-kutu').classList.add('gizli');
  });

  document.getElementById('form-yeni-menu').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const seciliYemekler = Array.from(document.querySelectorAll('#ym-yemek-listesi input:checked'))
        .map(cb => Number(cb.value));
      await apiIstek('/yonetici/menuler', {
        method: 'POST',
        body: JSON.stringify({
          yemekhane_id: Number(document.getElementById('ym-yemekhane').value),
          tarih: document.getElementById('ym-tarih').value,
          ogun_id: Number(document.getElementById('ym-ogun').value),
          ogrenci_fiyati: document.getElementById('ym-ogr-fiyat').value,
          personel_fiyati: document.getElementById('ym-per-fiyat').value,
          yemek_idler: seciliYemekler
        })
      });
      bildirimGoster('Yeni menü oluşturuldu ve yayına alındı.', 'basari');
      await yoneticiRaporYukle();
    } catch (err) {
      bildirimGoster(err.message, 'hata');
    }
  });

  try {
    aktifKullanici = await apiIstek('/ben');
    await oturumBaslat();
  } catch (_) {}
});