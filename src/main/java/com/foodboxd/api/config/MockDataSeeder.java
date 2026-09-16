package com.foodboxd.api.config;

import com.foodboxd.api.entities.*;
import com.foodboxd.api.repositories.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Geliştirme için hacimli sahte veri: İstanbul geneline yayılmış restoranlar,
 * menüler, kullanıcılar ve puanlar.
 *
 * <h2>Açma / kapama</h2>
 * <pre>
 *   app.seed.mock=true    → veriyi oluşturur; zaten varsa güncel tanımlara getirir
 *   app.seed.mock=false   → hiçbir şey yapmaz (varsayılan)
 *   app.seed.mock.wipe=true → sahte veriyi siler ve çıkar
 * </pre>
 *
 * <h2>Neden güvenli</h2>
 * Üretilen her restoran {@code seedTag="mock"} ile işaretlenir, her kullanıcının
 * e-postası {@code @mock.test} ile biter. Silme yalnızca bu işareti taşıyanlara
 * dokunur; senin kendi hesabın, kendi puanların ve {@link DataSeeder}'ın
 * ürettiği demo veri etkilenmez.
 *
 * <h2>Veri zaten varken</h2>
 * Silip yeniden üretmez — kimlikler değişir, senin sahte yemeklere verdiğin
 * puanlar giderdi. Onun yerine {@link #senkronla()} yemeklerin kategorisini ve
 * fotoğrafını güncel tablolara göre düzeltir, eksik restoran türlerini ekler.
 *
 * <h2>Puanlar neden rastgele değil</h2>
 * Düz rastgele puan her yemeğin ortalamasını 3.0'a yakınsatır; "En İyiler" gibi
 * sıralamalar anlamsızlaşır ve tasarım değerlendirilemez. Bunun yerine her
 * yemeğe bir "gerçek kalite" değeri atanır, puanlar onun etrafında gürültüyle
 * dağıtılır. Popülerlik de uzun kuyrukludur: birkaç yemek yüzlerce puan alır,
 * çoğu birkaç tane.
 *
 * <h2>Restoran adları</h2>
 * Üretilen adlar ilçe + tür kelimelerinden türetilmiş **uydurma** adlardır.
 * Gerçek işletmelere sahte puan iliştirmemek için gerçek isim kullanılmaz.
 *
 * <h2>Sahte kullanıcılarla giriş</h2>
 * Hepsinin şifresi aynı: {@code Deneme1234!} — e-posta
 * {@code kullanici001@mock.test} biçiminde.
 */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor
public class MockDataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final AddressRepository addressRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final YemekFotograflari fotograflar;

    @Value("${app.seed.mock:false}")
    private boolean mockEnabled;

    @Value("${app.seed.mock.wipe:false}")
    private boolean wipeRequested;

    // ── Hacim ─────────────────────────────────────────────────────────────────
    private static final int RESTORAN_SAYISI = 180;
    private static final int KULLANICI_SAYISI = 150;
    private static final int HEDEF_PUAN_SAYISI = 12_000;
    /** Ev yemeği türü sonradan eklendi; mevcut veriye bu kadar restoran eklenir. */
    private static final int EV_YEMEGI_EK_RESTORAN = 20;
    private static final String ETIKET = "mock";
    private static final String EPOSTA_ALANI = "@mock.test";
    private static final String SIFRE = "Deneme1234!";

    /** Sabit tohum: her çalıştırma aynı veriyi üretir, hata ayıklaması kolay olur. */
    private final Random rnd = new Random(20260912L);

    @Override
    @Transactional
    public void run(String... args) {
        if (wipeRequested) {
            wipe();
            return;
        }
        if (!mockEnabled) return;

        if (restaurantRepository.countBySeedTag(ETIKET) > 0) {
            senkronla();
            return;
        }

        long basla = System.currentTimeMillis();
        log.info("Sahte veri üretiliyor… (bu bir dakika sürebilir)");

        Map<String, Category> kategoriler = kategorileriHazirla();
        List<User> kullanicilar = kullanicilariUret();
        List<MenuItem> yemekler = restoranVeMenuleriUret(
                kategoriler, RESTORAN_SAYISI, null, new HashSet<>());
        puanlariUret(yemekler, kullanicilar);

        log.info("Sahte veri hazır: {} restoran, {} yemek, {} kullanıcı, {} puan ({} sn).",
                restaurantRepository.countBySeedTag(ETIKET), yemekler.size(),
                kullanicilar.size(), ratingRepository.count(),
                (System.currentTimeMillis() - basla) / 1000);
    }

    // ── Mevcut veriyi güncelleme ──────────────────────────────────────────────

    /**
     * Silmeden, mevcut sahte veriyi güncel tanımlara getirir:
     * <ul>
     *   <li>yemeğin kategorisi {@link #YEMEKLER} tablosuna göre düzeltilir
     *       (örn. lahmacun artık "Türk Mutfağı"),</li>
     *   <li>fotoğrafı havuzda olmayan yemek havuzdan fotoğraf alır,</li>
     *   <li>hiç "Ev Yemeği" restoranı yoksa eklenir.</li>
     * </ul>
     */
    private void senkronla() {
        Map<String, Category> kategoriler = kategorileriHazirla();

        int kategoriDuzeltilen = 0;
        int fotoDuzeltilen = 0;
        List<MenuItem> yemekler = menuItemRepository.findBySeedTag(ETIKET);
        for (MenuItem mi : yemekler) {
            String dogru = YEMEK_KATEGORISI.get(mi.getName());
            if (dogru != null && (mi.getCategory() == null
                    || !dogru.equals(mi.getCategory().getName()))) {
                mi.setCategory(kategoriler.get(dogru));
                kategoriDuzeltilen++;
            }
            List<String> havuz = fotograflar.fotolar(mi.getName());
            if (!havuz.isEmpty() && !havuz.contains(mi.getPhotoUrl())) {
                mi.setPhotoUrl(fotograflar.sec(mi.getName(), mi.getMenuItemId()));
                fotoDuzeltilen++;
            }
        }
        menuItemRepository.saveAll(yemekler);

        Set<String> adlar = new HashSet<>();
        boolean evYemegiVar = false;
        for (Restaurant r : restaurantRepository.findBySeedTag(ETIKET)) {
            adlar.add(r.getName());
            for (String tur : TUR.get("Ev Yemeği")) {
                if (r.getName().endsWith(tur)) evYemegiVar = true;
            }
        }

        int eklenen = 0;
        if (!evYemegiVar) {
            List<User> kullanicilar = entityManager
                    .createQuery("SELECT u FROM User u WHERE u.email LIKE :alan", User.class)
                    .setParameter("alan", "%" + EPOSTA_ALANI)
                    .getResultList();
            if (!kullanicilar.isEmpty()) {
                List<MenuItem> yeni = restoranVeMenuleriUret(
                        kategoriler, EV_YEMEGI_EK_RESTORAN, "Ev Yemeği", adlar);
                puanlariUret(yeni, kullanicilar);
                eklenen = EV_YEMEGI_EK_RESTORAN;
            }
        }

        log.info("Sahte veri güncellendi: {} yemeğin kategorisi, {} yemeğin fotoğrafı "
                + "düzeltildi, {} ev yemeği restoranı eklendi.",
                kategoriDuzeltilen, fotoDuzeltilen, eklenen);
    }

    // ── Silme ─────────────────────────────────────────────────────────────────

    /**
     * Yalnızca işaretli veriyi siler. Sıra önemli: yabancı anahtarlar yüzünden
     * önce yapraklar (puan, istek listesi), sonra gövde (yemek, restoran, adres).
     */
    private void wipe() {
        long restoran = restaurantRepository.countBySeedTag(ETIKET);
        if (restoran == 0) {
            log.info("Silinecek sahte veri yok.");
            return;
        }
        log.info("Sahte veri siliniyor…");

        int puan = entityManager.createNativeQuery("""
                DELETE FROM ratings WHERE menu_item_id IN (
                  SELECT mi.menu_item_id FROM menu_items mi
                  JOIN restaurants r ON r.restaurant_id = mi.restaurant_id
                  WHERE r.seed_tag = :tag)
                """).setParameter("tag", ETIKET).executeUpdate();

        entityManager.createNativeQuery("""
                DELETE FROM wishlist_items WHERE menu_item_id IN (
                  SELECT mi.menu_item_id FROM menu_items mi
                  JOIN restaurants r ON r.restaurant_id = mi.restaurant_id
                  WHERE r.seed_tag = :tag)
                """).setParameter("tag", ETIKET).executeUpdate();

        int yemek = entityManager.createNativeQuery("""
                DELETE FROM menu_items WHERE restaurant_id IN (
                  SELECT restaurant_id FROM restaurants WHERE seed_tag = :tag)
                """).setParameter("tag", ETIKET).executeUpdate();

        entityManager.createNativeQuery(
                        "CREATE TEMP TABLE IF NOT EXISTS silinecek_adres AS "
                                + "SELECT address_id FROM restaurants WHERE seed_tag = :tag")
                .setParameter("tag", ETIKET).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM restaurants WHERE seed_tag = :tag")
                .setParameter("tag", ETIKET).executeUpdate();

        entityManager.createNativeQuery(
                "DELETE FROM addresses WHERE address_id IN (SELECT address_id FROM silinecek_adres)")
                .executeUpdate();
        entityManager.createNativeQuery("DROP TABLE IF EXISTS silinecek_adres").executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM refresh_tokens WHERE user_id IN "
                                + "(SELECT user_id FROM users WHERE email LIKE :alan)")
                .setParameter("alan", "%" + EPOSTA_ALANI).executeUpdate();
        entityManager.createNativeQuery(
                        "DELETE FROM ratings WHERE user_id IN "
                                + "(SELECT user_id FROM users WHERE email LIKE :alan)")
                .setParameter("alan", "%" + EPOSTA_ALANI).executeUpdate();
        int kullanici = entityManager.createNativeQuery(
                        "DELETE FROM users WHERE email LIKE :alan")
                .setParameter("alan", "%" + EPOSTA_ALANI).executeUpdate();

        log.info("Sahte veri silindi: {} restoran, {} yemek, {} puan, {} kullanıcı.",
                restoran, yemek, puan, kullanici);
    }

    // ── Kategoriler ───────────────────────────────────────────────────────────

    private Map<String, Category> kategorileriHazirla() {
        Map<String, Category> map = new LinkedHashMap<>();
        for (String ad : KATEGORILER) {
            map.put(ad, categoryRepository.findByName(ad)
                    .orElseGet(() -> categoryRepository.save(Category.builder().name(ad).build())));
        }
        return map;
    }

    // ── Kullanıcılar ──────────────────────────────────────────────────────────

    private List<User> kullanicilariUret() {
        String hash = passwordEncoder.encode(SIFRE);   // bir kez; bcrypt pahalı
        List<User> liste = new ArrayList<>(KULLANICI_SAYISI);
        for (int i = 1; i <= KULLANICI_SAYISI; i++) {
            String no = String.format("%03d", i);
            liste.add(User.builder()
                    .username("kullanici" + no)
                    .firstName(AD[rnd.nextInt(AD.length)])
                    .lastName(SOYAD[rnd.nextInt(SOYAD.length)])
                    .email("kullanici" + no + EPOSTA_ALANI)
                    .passwordHash(hash)
                    .role(UserRole.USER)
                    .build());
        }
        return userRepository.saveAll(liste);
    }

    // ── Restoranlar ve menüler ────────────────────────────────────────────────

    /**
     * @param sabitKategori {@code null} → her restoranın türü rastgele
     * @param kullanilanAdlar mevcut restoran adları; yeni adlar bunlarla çakışmaz
     */
    private List<MenuItem> restoranVeMenuleriUret(Map<String, Category> kategoriler, int adet,
                                                  String sabitKategori,
                                                  Set<String> kullanilanAdlar) {
        List<MenuItem> tumYemekler = new ArrayList<>();

        for (int i = 0; i < adet; i++) {
            String kategori = sabitKategori != null ? sabitKategori
                    : KATEGORILER[rnd.nextInt(KATEGORILER.length)];
            Ilce ilce = ILCELER[agirlikliIlce()];

            String ad = benzersizAd(kategori, ilce.ad(), kullanilanAdlar);

            // Koordinat: ilçe merkezinin çevresine dağıt (~1.5 km)
            double lat = ilce.lat() + (rnd.nextDouble() - 0.5) * 0.025;
            double lng = ilce.lng() + (rnd.nextDouble() - 0.5) * 0.030;

            Address adres = addressRepository.save(Address.builder()
                    .city("İstanbul")
                    .district(ilce.ad())
                    .neighbourhood(MAHALLE[rnd.nextInt(MAHALLE.length)] + " Mah.")
                    .addressLine1(CADDE[rnd.nextInt(CADDE.length)])
                    .buildingNo(String.valueOf(1 + rnd.nextInt(120)))
                    .fullAddress(ad + ", " + ilce.ad() + "/İstanbul")
                    .latitude(lat)
                    .longitude(lng)
                    .build());

            Restaurant restoran = restaurantRepository.save(Restaurant.builder()
                    .name(ad)
                    .address(adres)
                    .seedTag(ETIKET)
                    .build());

            tumYemekler.addAll(menuUret(restoran, kategori, kategoriler));
        }
        return tumYemekler;
    }

    /** Restoranın ana kategorisinden çoğunluk, yanına birkaç yan kategori. */
    private List<MenuItem> menuUret(Restaurant restoran, String anaKategori,
                                    Map<String, Category> kategoriler) {
        int adet = 8 + rnd.nextInt(5);                 // 8–12
        List<MenuItem> yemekler = new ArrayList<>(adet);
        Set<String> adlar = new HashSet<>();

        for (int i = 0; i < adet; i++) {
            // İlk %70 ana kategoriden; gerisi tatlı/içecek gibi yan kategorilerden
            String kategori = (i < adet * 0.7) ? anaKategori
                    : YAN_KATEGORILER[rnd.nextInt(YAN_KATEGORILER.length)];
            String[] havuz = YEMEKLER.get(kategori);
            String ad = havuz[rnd.nextInt(havuz.length)];
            if (!adlar.add(ad)) continue;              // aynı restoranda tekrar etmesin

            int[] aralik = FIYAT.get(kategori);
            int fiyat = aralik[0] + rnd.nextInt(aralik[1] - aralik[0] + 1);
            fiyat = (fiyat / 5) * 5;                   // 5'in katına yuvarla

            yemekler.add(MenuItem.builder()
                    .restaurant(restoran)
                    .category(kategoriler.get(kategori))
                    .name(ad)
                    .price(BigDecimal.valueOf(fiyat))
                    .photoUrl(fotograflar.sec(ad, rnd.nextInt(1_000)))
                    .averageRating(BigDecimal.ZERO)
                    .ratingCount(0)
                    .build());
        }
        return menuItemRepository.saveAll(yemekler);
    }

    // ── Puanlar ───────────────────────────────────────────────────────────────

    /**
     * Her yemeğe bir "gerçek kalite" atanır (çoğu 3.5–4.5, az sayıda uç örnek).
     * Puan sayısı uzun kuyruklu: az sayıda yemek çok puan alır.
     */
    private void puanlariUret(List<MenuItem> yemekler, List<User> kullanicilar) {
        List<Rating> tampon = new ArrayList<>(1000);
        int toplam = 0;

        // Yemek başına düşen ortalama puan sayısı. Tüm üretimdeki yemek sayısına
        // göre sabit: sonradan eklenen 20 restoranlık bir grup kendi küçük
        // yemek sayısına bölünseydi yemek başına onlarca puan alırdı.
        double ortalamaPuanSayisi = (double) HEDEF_PUAN_SAYISI / (RESTORAN_SAYISI * 7);

        for (MenuItem yemek : yemekler) {
            double kalite = kaliteUret();
            int kacPuan = populerlikUret(ortalamaPuanSayisi);
            kacPuan = Math.min(kacPuan, kullanicilar.size());
            if (kacPuan == 0) continue;

            // Aynı kullanıcı aynı yemeği bir kez puanlayabilir (benzersizlik kısıtı)
            List<User> karisik = new ArrayList<>(kullanicilar);
            Collections.shuffle(karisik, rnd);

            double toplamSkor = 0;
            for (int i = 0; i < kacPuan; i++) {
                double skor = yarimYildizaYuvarla(kalite + rnd.nextGaussian() * 0.55);
                toplamSkor += skor;
                tampon.add(Rating.builder()
                        .user(karisik.get(i))
                        .menuItem(yemek)
                        .score(BigDecimal.valueOf(skor))
                        .comment(rnd.nextDouble() < 0.30 ? yorumUret(skor) : null)
                        .build());
            }
            yemek.setAverageRating(BigDecimal.valueOf(toplamSkor / kacPuan)
                    .setScale(2, RoundingMode.HALF_UP));
            yemek.setRatingCount(kacPuan);
            toplam += kacPuan;

            if (tampon.size() >= 1000) {
                ratingRepository.saveAll(tampon);
                tampon.clear();
            }
        }
        if (!tampon.isEmpty()) ratingRepository.saveAll(tampon);
        menuItemRepository.saveAll(yemekler);
        log.info("  {} puan üretildi.", toplam);
    }

    /** Çoğu yemek 3.5–4.5; küçük bir kısmı gerçekten iyi ya da gerçekten kötü. */
    private double kaliteUret() {
        double u = rnd.nextDouble();
        if (u < 0.07) return 2.0 + rnd.nextDouble() * 1.0;   // kötü
        if (u > 0.93) return 4.6 + rnd.nextDouble() * 0.4;   // çok iyi
        return 3.4 + rnd.nextDouble() * 1.2;
    }

    /** Uzun kuyruk: çoğu yemek az puan alır, birkaçı çok. */
    private int populerlikUret(double ortalama) {
        double u = rnd.nextDouble();
        double carpan = u < 0.70 ? 0.25          // çoğunluk
                : u < 0.95 ? 1.5                 // orta
                : 6.0;                           // popüler azınlık
        return (int) Math.round(ortalama * carpan * (0.5 + rnd.nextDouble()));
    }

    private double yarimYildizaYuvarla(double ham) {
        double sinirli = Math.max(0.5, Math.min(5.0, ham));
        return Math.round(sinirli * 2) / 2.0;
    }

    private String yorumUret(double skor) {
        String[] havuz = skor >= 4.5 ? YORUM_IYI : skor >= 3.0 ? YORUM_ORTA : YORUM_KOTU;
        return havuz[rnd.nextInt(havuz.length)];
    }

    // ── Ad üretimi ────────────────────────────────────────────────────────────

    private String benzersizAd(String kategori, String ilce, Set<String> kullanilan) {
        String[] turler = TUR.get(kategori);
        for (int deneme = 0; deneme < 40; deneme++) {
            String on = rnd.nextBoolean() ? ilce : ON_EK[rnd.nextInt(ON_EK.length)];
            String ad = on + " " + turler[rnd.nextInt(turler.length)];
            if (kullanilan.add(ad)) return ad;
        }
        String ad = ilce + " " + turler[0] + " " + (kullanilan.size() + 1);
        kullanilan.add(ad);
        return ad;
    }

    /** Merkez ilçeler daha yoğun — gerçek dağılıma benzesin. */
    private int agirlikliIlce() {
        double u = rnd.nextDouble();
        if (u < 0.45) return rnd.nextInt(5);        // ilk 5 ilçe: merkez
        if (u < 0.80) return 5 + rnd.nextInt(6);
        return 11 + rnd.nextInt(ILCELER.length - 11);
    }

    // ── Sabitler ──────────────────────────────────────────────────────────────

    private record Ilce(String ad, double lat, double lng) {}

    private static final Ilce[] ILCELER = {
            new Ilce("Kadıköy", 40.9903, 29.0270), new Ilce("Beşiktaş", 41.0430, 29.0090),
            new Ilce("Şişli", 41.0602, 28.9877), new Ilce("Beyoğlu", 41.0350, 28.9770),
            new Ilce("Üsküdar", 41.0233, 29.0152), new Ilce("Fatih", 41.0186, 28.9400),
            new Ilce("Bakırköy", 40.9820, 28.8720), new Ilce("Ataşehir", 40.9920, 29.1270),
            new Ilce("Maltepe", 40.9350, 29.1300), new Ilce("Sarıyer", 41.1670, 29.0580),
            new Ilce("Zeytinburnu", 40.9920, 28.9020), new Ilce("Bağcılar", 41.0353, 28.8560),
            new Ilce("Kartal", 40.8870, 29.1900), new Ilce("Pendik", 40.8770, 29.2330),
            new Ilce("Eyüpsultan", 41.0480, 28.9330), new Ilce("Güngören", 41.0200, 28.8760),
            new Ilce("Beylikdüzü", 41.0010, 28.6410), new Ilce("Esenyurt", 41.0340, 28.6800),
    };

    private static final String[] KATEGORILER = {
            "Burger", "Pizza", "Türk Mutfağı", "Ev Yemeği", "Sushi", "Tatlı", "Kahvaltı",
            "İtalyan", "Vegan", "Meze", "Noodle", "Tavuk", "Sandviç"};

    private static final String[] YAN_KATEGORILER = {"Tatlı", "Vegan", "Sandviç"};

    private static final String[] ON_EK = {
            "Mavi", "Köşe", "Sokak", "Liman", "Bereket", "Keyif", "Nar", "Zeytin",
            "Ustam", "Dede", "Çınar", "Sahil", "Kubbe", "Lezzet", "Meydan", "Fırın"};

    private static final Map<String, String[]> TUR = Map.ofEntries(
            Map.entry("Burger", new String[]{"Burger Evi", "Burger House", "Smash Co.", "Grill Bar"}),
            Map.entry("Pizza", new String[]{"Pizzeria", "Forno", "Pizza Evi"}),
            Map.entry("Türk Mutfağı", new String[]{"Ocakbaşı", "Kebap Salonu", "Döner Evi", "Kebapçı", "Pide Salonu"}),
            Map.entry("Ev Yemeği", new String[]{"Lokantası", "Ev Yemekleri", "Esnaf Lokantası", "Sofrası"}),
            Map.entry("Sushi", new String[]{"Sushi Bar", "Sushi Han", "Omakase"}),
            Map.entry("Tatlı", new String[]{"Tatlıcı", "Pastane", "Baklavacı"}),
            Map.entry("Kahvaltı", new String[]{"Kahvaltı Evi", "Brunch Kulübü", "Kahvaltıcı"}),
            Map.entry("İtalyan", new String[]{"Trattoria", "Cucina", "Pasta Evi"}),
            Map.entry("Vegan", new String[]{"Vegan Mutfak", "Green Kitchen", "Bitki Mutfağı"}),
            Map.entry("Meze", new String[]{"Meyhane", "Meze Evi", "Balıkçı"}),
            Map.entry("Noodle", new String[]{"Noodle Bar", "Wok Evi", "Ramen Evi"}),
            Map.entry("Tavuk", new String[]{"Tavukçu", "Chicken House", "Izgara Evi"}),
            Map.entry("Sandviç", new String[]{"Sandviç Dükkanı", "Tost Evi", "Büfe"}));

    /**
     * Kategori → yemekler. Her yemek yalnızca bir kategoride durur; mevcut
     * verinin kategorisi de bu tabloya göre düzeltilir ({@link #senkronla()}).
     * Fotoğraflar yemek adıyla eşleşir: yeni yemek eklersen
     * {@code mock/yemek-fotograflari.json}'a da ekle.
     */
    private static final Map<String, String[]> YEMEKLER = Map.ofEntries(
            Map.entry("Burger", new String[]{"Smash Burger", "Cheeseburger", "Double Cheddar",
                    "Tavuklu Burger", "Mantarlı Burger", "Acılı Burger", "Patates Kızartması",
                    "Soğan Halkası", "Trüflü Patates"}),
            Map.entry("Pizza", new String[]{"Margherita", "Pepperoni", "Dört Peynirli",
                    "Sucuklu Pizza", "Mantarlı Pizza", "Karışık Pizza", "Calzone", "Beyaz Pizza"}),
            Map.entry("Türk Mutfağı", new String[]{"Adana Kebap", "Urfa Kebap", "Kuzu Şiş",
                    "Beyti", "İskender", "Dürüm Döner", "Ekmek Arası Döner", "Tantuni",
                    "Patlıcan Kebabı", "Lahmacun", "Kıymalı Pide", "Kuşbaşılı Pide", "Çiğ Köfte",
                    "Ali Nazik", "Hünkar Beğendi"}),
            Map.entry("Ev Yemeği", new String[]{"Kuru Fasulye", "Pilav", "Mantı", "Karnıyarık",
                    "Etli Nohut", "Yaprak Sarma", "Taze Fasulye", "Mercimek Çorbası",
                    "Ezogelin Çorbası", "Biber Dolması", "Musakka", "Türlü", "Tas Kebabı",
                    "İzmir Köfte"}),
            Map.entry("Sushi", new String[]{"Somon Nigiri", "Ton Balığı Nigiri", "California Roll",
                    "Dragon Roll", "Sashimi Tabağı", "Omakase Seti", "Gyoza", "Miso Çorbası"}),
            Map.entry("Tatlı", new String[]{"Künefe", "Fıstıklı Baklava", "Sütlaç", "Tiramisu",
                    "Cheesecake", "Çikolatalı Sufle", "Profiterol", "Dondurma", "Magnolia",
                    "Milkshake"}),
            Map.entry("Kahvaltı", new String[]{"Serpme Kahvaltı", "Menemen", "Sahanda Yumurta",
                    "Karışık Gözleme", "Simit Tabağı", "Avokadolu Tost", "Pancake", "Omlet"}),
            Map.entry("İtalyan", new String[]{"Carbonara", "Bolonez", "Pesto Makarna", "Risotto",
                    "Lazanya", "Bruschetta", "Caprese Salata"}),
            Map.entry("Vegan", new String[]{"Falafel Tabağı", "Buddha Bowl", "Mercimek Köftesi",
                    "Humus", "Vegan Burger", "Kinoa Salatası", "Sebze Wrap"}),
            Map.entry("Meze", new String[]{"Haydari", "Atom", "Fava", "Şakşuka", "Midye Dolma",
                    "Cacık", "Enginar", "Patlıcan Salatası"}),
            Map.entry("Noodle", new String[]{"Ramen", "Pad Thai", "Yakisoba", "Udon",
                    "Acılı Miso Ramen", "Wok Noodle"}),
            Map.entry("Tavuk", new String[]{"Izgara Tavuk", "Tavuk Kanat", "Crispy Tavuk",
                    "Tavuklu Pilav", "Tavuk Şiş", "Buffalo Kanat"}),
            Map.entry("Sandviç", new String[]{"Kumru", "Ayvalık Tostu", "Club Sandviç",
                    "Tavuklu Wrap", "Falafel Dürüm", "Kaşarlı Tost"}));

    /** Yemek adı → kategori (YEMEKLER'in tersi). */
    private static final Map<String, String> YEMEK_KATEGORISI = new HashMap<>();

    static {
        YEMEKLER.forEach((kategori, yemekler) -> {
            for (String y : yemekler) {
                String onceki = YEMEK_KATEGORISI.put(y, kategori);
                if (onceki != null) {
                    throw new IllegalStateException(y + " iki kategoride: " + onceki + ", " + kategori);
                }
            }
        });
    }

    private static final Map<String, int[]> FIYAT = Map.ofEntries(
            Map.entry("Burger", new int[]{180, 520}), Map.entry("Pizza", new int[]{200, 480}),
            Map.entry("Türk Mutfağı", new int[]{120, 550}), Map.entry("Ev Yemeği", new int[]{90, 320}),
            Map.entry("Sushi", new int[]{280, 1200}),
            Map.entry("Tatlı", new int[]{80, 260}), Map.entry("Kahvaltı", new int[]{90, 650}),
            Map.entry("İtalyan", new int[]{220, 560}), Map.entry("Vegan", new int[]{120, 320}),
            Map.entry("Meze", new int[]{70, 280}), Map.entry("Noodle", new int[]{200, 420}),
            Map.entry("Tavuk", new int[]{150, 380}), Map.entry("Sandviç", new int[]{80, 260}));

    private static final String[] AD = {"Ahmet", "Ayşe", "Mehmet", "Elif", "Mustafa", "Zeynep",
            "Emre", "Fatma", "Burak", "Merve", "Can", "Selin", "Kerem", "Deniz", "Okan", "Ece",
            "Serkan", "Buse", "Onur", "İrem", "Tolga", "Ceren", "Barış", "Gizem"};
    private static final String[] SOYAD = {"Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız",
            "Yıldırım", "Öztürk", "Aydın", "Özdemir", "Arslan", "Doğan", "Kılıç", "Aslan", "Çetin"};

    private static final String[] MAHALLE = {"Caferağa", "Osmanağa", "Rasimpaşa", "Sinanpaşa",
            "Levent", "Etiler", "Cihangir", "Galata", "Moda", "Fenerbahçe", "Acıbadem", "Koşuyolu"};
    private static final String[] CADDE = {"Bağdat Cad.", "İstiklal Cad.", "Bahariye Cad.",
            "Halaskargazi Cad.", "Nispetiye Cad.", "Rumeli Cad.", "Moda Cad.", "Sahil Yolu"};

    private static final String[] YORUM_IYI = {
            "Beklediğimden çok daha iyiydi, kesinlikle tekrar geleceğim.",
            "Porsiyon doyurucu, lezzet tam kıvamında.",
            "Buranın en iyi işi bu bence.", "Arkadaşlara da önerdim, hepsi beğendi.",
            "Fiyatına göre gerçekten başarılı.", "Sıcacık geldi, sunumu da güzeldi."};
    private static final String[] YORUM_ORTA = {
            "Fena değil ama bir daha söyler miyim bilmem.",
            "Lezzet iyiydi, porsiyon biraz küçük geldi.",
            "Ortalama. Beklentimi karşıladı sayılır.",
            "İyiydi ama fiyatı biraz yüksek.", "Gayet standart, sürprizi yok."};
    private static final String[] YORUM_KOTU = {
            "Maalesef beğenmedim, soğuk geldi.",
            "Fiyatına göre kesinlikle değmez.",
            "Tadı beklediğim gibi değildi.", "Bir daha denemem açıkçası."};
}
