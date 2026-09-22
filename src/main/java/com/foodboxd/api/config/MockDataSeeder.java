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
 * puanlar giderdi. Onun yerine {@link #sync()} yemeklerin kategorisini ve
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
    private final DishPhotos photos;

    @Value("${app.seed.mock:false}")
    private boolean mockEnabled;

    @Value("${app.seed.mock.wipe:false}")
    private boolean wipeRequested;

    // ── Hacim ─────────────────────────────────────────────────────────────────
    private static final int RESTAURANT_COUNT = 180;
    private static final int USER_COUNT = 150;
    private static final int TARGET_RATING_COUNT = 12_000;
    /** Ev yemeği türü sonradan eklendi; mevcut veriye bu kadar restoran eklenir. */
    private static final int EXTRA_HOME_COOKING_RESTAURANTS = 20;
    private static final String SEED_TAG = "mock";
    private static final String EMAIL_DOMAIN = "@mock.test";
    private static final String PASSWORD = "Deneme1234!";

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

        if (restaurantRepository.countBySeedTag(SEED_TAG) > 0) {
            sync();
            return;
        }

        long start = System.currentTimeMillis();
        log.info("Sahte veri üretiliyor… (bu bir dakika sürebilir)");

        Map<String, Category> categories = prepareCategories();
        List<User> users = generateUsers();
        List<MenuItem> dishes = generateRestaurantsAndMenus(
                categories, RESTAURANT_COUNT, null, new HashSet<>());
        generateRatings(dishes, users);

        log.info("Sahte veri hazır: {} restoran, {} yemek, {} kullanıcı, {} puan ({} sn).",
                restaurantRepository.countBySeedTag(SEED_TAG), dishes.size(),
                users.size(), ratingRepository.count(),
                (System.currentTimeMillis() - start) / 1000);
    }

    // ── Mevcut veriyi güncelleme ──────────────────────────────────────────────

    /**
     * Silmeden, mevcut sahte veriyi güncel tanımlara getirir:
     * <ul>
     *   <li>yemeğin kategorisi {@link #DISHES} tablosuna göre düzeltilir
     *       (örn. lahmacun artık "Türk Mutfağı"),</li>
     *   <li>fotoğrafı havuzda olmayan yemek havuzdan fotoğraf alır,</li>
     *   <li>hiç "Ev Yemeği" restoranı yoksa eklenir.</li>
     * </ul>
     */
    private void sync() {
        Map<String, Category> categories = prepareCategories();

        int categoriesFixed = 0;
        int photosFixed = 0;
        List<MenuItem> dishes = menuItemRepository.findBySeedTag(SEED_TAG);
        for (MenuItem mi : dishes) {
            String correctCategory = DISH_CATEGORY.get(mi.getName());
            if (correctCategory != null && (mi.getCategory() == null
                    || !correctCategory.equals(mi.getCategory().getName()))) {
                mi.setCategory(categories.get(correctCategory));
                categoriesFixed++;
            }
            List<String> pool = photos.photos(mi.getName());
            if (!pool.isEmpty() && !pool.contains(mi.getPhotoUrl())) {
                mi.setPhotoUrl(photos.pick(mi.getName(), mi.getMenuItemId()));
                photosFixed++;
            }
        }
        menuItemRepository.saveAll(dishes);

        Set<String> names = new HashSet<>();
        boolean hasHomeCooking = false;
        for (Restaurant r : restaurantRepository.findBySeedTag(SEED_TAG)) {
            names.add(r.getName());
            for (String cuisine : CUISINE_NAMES.get("Ev Yemeği")) {
                if (r.getName().endsWith(cuisine)) hasHomeCooking = true;
            }
        }

        int added = 0;
        if (!hasHomeCooking) {
            List<User> users = entityManager
                    .createQuery("SELECT u FROM User u WHERE u.email LIKE :domain", User.class)
                    .setParameter("domain", "%" + EMAIL_DOMAIN)
                    .getResultList();
            if (!users.isEmpty()) {
                List<MenuItem> fresh = generateRestaurantsAndMenus(
                        categories, EXTRA_HOME_COOKING_RESTAURANTS, "Ev Yemeği", names);
                generateRatings(fresh, users);
                added = EXTRA_HOME_COOKING_RESTAURANTS;
            }
        }

        log.info("Sahte veri güncellendi: {} yemeğin kategorisi, {} yemeğin fotoğrafı "
                + "düzeltildi, {} ev yemeği restoranı eklendi.",
                categoriesFixed, photosFixed, added);
    }

    // ── Silme ─────────────────────────────────────────────────────────────────

    /**
     * Yalnızca işaretli veriyi siler. Sıra önemli: yabancı anahtarlar yüzünden
     * önce yapraklar (puan, istek listesi), sonra gövde (yemek, restoran, adres).
     */
    private void wipe() {
        long restaurantCount = restaurantRepository.countBySeedTag(SEED_TAG);
        if (restaurantCount == 0) {
            log.info("Silinecek sahte veri yok.");
            return;
        }
        log.info("Sahte veri siliniyor…");

        int ratingCount = entityManager.createNativeQuery("""
                DELETE FROM ratings WHERE menu_item_id IN (
                  SELECT mi.menu_item_id FROM menu_items mi
                  JOIN restaurants r ON r.restaurant_id = mi.restaurant_id
                  WHERE r.seed_tag = :tag)
                """).setParameter("tag", SEED_TAG).executeUpdate();

        entityManager.createNativeQuery("""
                DELETE FROM wishlist_items WHERE menu_item_id IN (
                  SELECT mi.menu_item_id FROM menu_items mi
                  JOIN restaurants r ON r.restaurant_id = mi.restaurant_id
                  WHERE r.seed_tag = :tag)
                """).setParameter("tag", SEED_TAG).executeUpdate();

        int dishCount = entityManager.createNativeQuery("""
                DELETE FROM menu_items WHERE restaurant_id IN (
                  SELECT restaurant_id FROM restaurants WHERE seed_tag = :tag)
                """).setParameter("tag", SEED_TAG).executeUpdate();

        entityManager.createNativeQuery(
                        "CREATE TEMP TABLE IF NOT EXISTS addresses_to_delete AS "
                                + "SELECT address_id FROM restaurants WHERE seed_tag = :tag")
                .setParameter("tag", SEED_TAG).executeUpdate();

        entityManager.createNativeQuery("DELETE FROM restaurants WHERE seed_tag = :tag")
                .setParameter("tag", SEED_TAG).executeUpdate();

        entityManager.createNativeQuery(
                "DELETE FROM addresses WHERE address_id IN (SELECT address_id FROM addresses_to_delete)")
                .executeUpdate();
        entityManager.createNativeQuery("DROP TABLE IF EXISTS addresses_to_delete").executeUpdate();

        entityManager.createNativeQuery(
                        "DELETE FROM refresh_tokens WHERE user_id IN "
                                + "(SELECT user_id FROM users WHERE email LIKE :domain)")
                .setParameter("domain", "%" + EMAIL_DOMAIN).executeUpdate();
        entityManager.createNativeQuery(
                        "DELETE FROM ratings WHERE user_id IN "
                                + "(SELECT user_id FROM users WHERE email LIKE :domain)")
                .setParameter("domain", "%" + EMAIL_DOMAIN).executeUpdate();
        int userCount = entityManager.createNativeQuery(
                        "DELETE FROM users WHERE email LIKE :domain")
                .setParameter("domain", "%" + EMAIL_DOMAIN).executeUpdate();

        log.info("Sahte veri silindi: {} restoran, {} yemek, {} puan, {} kullanıcı.",
                restaurantCount, dishCount, ratingCount, userCount);
    }

    // ── Kategoriler ───────────────────────────────────────────────────────────

    private Map<String, Category> prepareCategories() {
        Map<String, Category> map = new LinkedHashMap<>();
        for (String name : CATEGORIES) {
            map.put(name, categoryRepository.findByName(name)
                    .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build())));
        }
        return map;
    }

    // ── Kullanıcılar ──────────────────────────────────────────────────────────

    private List<User> generateUsers() {
        String hash = passwordEncoder.encode(PASSWORD);   // bir kez; bcrypt pahalı
        List<User> list = new ArrayList<>(USER_COUNT);
        for (int i = 1; i <= USER_COUNT; i++) {
            String no = String.format("%03d", i);
            list.add(User.builder()
                    .username("kullanici" + no)
                    .firstName(FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)])
                    .lastName(LAST_NAMES[rnd.nextInt(LAST_NAMES.length)])
                    .email("kullanici" + no + EMAIL_DOMAIN)
                    .passwordHash(hash)
                    .role(UserRole.USER)
                    .build());
        }
        return userRepository.saveAll(list);
    }

    // ── Restoranlar ve menüler ────────────────────────────────────────────────

    /**
     * @param sabitKategori {@code null} → her restoranın türü rastgele
     * @param kullanilanAdlar mevcut restoran adları; yeni adlar bunlarla çakışmaz
     */
    private List<MenuItem> generateRestaurantsAndMenus(Map<String, Category> categories, int count,
                                                  String fixedCategory,
                                                  Set<String> usedNames) {
        List<MenuItem> allDishes = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String category = fixedCategory != null ? fixedCategory
                    : CATEGORIES[rnd.nextInt(CATEGORIES.length)];
            District district = DISTRICTS[weightedDistrict()];

            String name = uniqueName(category, district.name(), usedNames);

            // Koordinat: ilçe merkezinin çevresine dağıt (~1.5 km)
            double lat = district.lat() + (rnd.nextDouble() - 0.5) * 0.025;
            double lng = district.lng() + (rnd.nextDouble() - 0.5) * 0.030;

            Address address = addressRepository.save(Address.builder()
                    .city("İstanbul")
                    .district(district.name())
                    .neighbourhood(NEIGHBORHOODS[rnd.nextInt(NEIGHBORHOODS.length)] + " Mah.")
                    .addressLine1(STREETS[rnd.nextInt(STREETS.length)])
                    .buildingNo(String.valueOf(1 + rnd.nextInt(120)))
                    .fullAddress(name + ", " + district.name() + "/İstanbul")
                    .latitude(lat)
                    .longitude(lng)
                    .build());

            Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                    .name(name)
                    .address(address)
                    .seedTag(SEED_TAG)
                    .build());

            allDishes.addAll(generateMenu(restaurant, category, categories));
        }
        return allDishes;
    }

    /** Restoranın ana kategorisinden çoğunluk, yanına birkaç yan kategori. */
    private List<MenuItem> generateMenu(Restaurant restaurant, String primaryCategory,
                                    Map<String, Category> categories) {
        int count = 8 + rnd.nextInt(5);                 // 8–12
        List<MenuItem> dishes = new ArrayList<>(count);
        Set<String> names = new HashSet<>();

        for (int i = 0; i < count; i++) {
            // İlk %70 ana kategoriden; gerisi tatlı/içecek gibi yan kategorilerden
            String category = (i < count * 0.7) ? primaryCategory
                    : SIDE_CATEGORIES[rnd.nextInt(SIDE_CATEGORIES.length)];
            String[] pool = DISHES.get(category);
            String name = pool[rnd.nextInt(pool.length)];
            if (!names.add(name)) continue;              // aynı restoranda tekrar etmesin

            dishes.add(MenuItem.builder()
                    .restaurant(restaurant)
                    .category(categories.get(category))
                    .name(name)
                    .photoUrl(photos.pick(name, rnd.nextInt(1_000)))
                    .averageRating(BigDecimal.ZERO)
                    .ratingCount(0)
                    .build());
        }
        return menuItemRepository.saveAll(dishes);
    }

    // ── Puanlar ───────────────────────────────────────────────────────────────

    /**
     * Her yemeğe bir "gerçek kalite" atanır (çoğu 3.5–4.5, az sayıda uç örnek).
     * Puan sayısı uzun kuyruklu: az sayıda yemek çok puan alır.
     */
    private void generateRatings(List<MenuItem> dishes, List<User> users) {
        List<Rating> tampon = new ArrayList<>(1000);
        int total = 0;

        // Yemek başına düşen ortalama puan sayısı. Tüm üretimdeki yemek sayısına
        // göre sabit: sonradan eklenen 20 restoranlık bir grup kendi küçük
        // yemek sayısına bölünseydi yemek başına onlarca puan alırdı.
        double averageRatingCount = (double) TARGET_RATING_COUNT / (RESTAURANT_COUNT * 7);

        for (MenuItem dish : dishes) {
            double quality = generateQuality();
            int ratingCount = generatePopularity(averageRatingCount);
            ratingCount = Math.min(ratingCount, users.size());
            if (ratingCount == 0) continue;

            // Aynı kullanıcı aynı yemeği bir kez puanlayabilir (benzersizlik kısıtı)
            List<User> shuffled = new ArrayList<>(users);
            Collections.shuffle(shuffled, rnd);

            double totalScore = 0;
            for (int i = 0; i < ratingCount; i++) {
                double score = roundToHalfStar(quality + rnd.nextGaussian() * 0.55);
                totalScore += score;
                tampon.add(Rating.builder()
                        .user(shuffled.get(i))
                        .menuItem(dish)
                        .score(BigDecimal.valueOf(score))
                        .comment(rnd.nextDouble() < 0.30 ? generateComment(score) : null)
                        .build());
            }
            dish.setAverageRating(BigDecimal.valueOf(totalScore / ratingCount)
                    .setScale(2, RoundingMode.HALF_UP));
            dish.setRatingCount(ratingCount);
            total += ratingCount;

            if (tampon.size() >= 1000) {
                ratingRepository.saveAll(tampon);
                tampon.clear();
            }
        }
        if (!tampon.isEmpty()) ratingRepository.saveAll(tampon);
        menuItemRepository.saveAll(dishes);
        log.info("  {} puan üretildi.", total);
    }

    /** Çoğu yemek 3.5–4.5; küçük bir kısmı gerçekten iyi ya da gerçekten kötü. */
    private double generateQuality() {
        double u = rnd.nextDouble();
        if (u < 0.07) return 2.0 + rnd.nextDouble() * 1.0;   // kötü
        if (u > 0.93) return 4.6 + rnd.nextDouble() * 0.4;   // çok iyi
        return 3.4 + rnd.nextDouble() * 1.2;
    }

    /** Uzun kuyruk: çoğu yemek az puan alır, birkaçı çok. */
    private int generatePopularity(double average) {
        double u = rnd.nextDouble();
        double factor = u < 0.70 ? 0.25          // çoğunluk
                : u < 0.95 ? 1.5                 // orta
                : 6.0;                           // popüler azınlık
        return (int) Math.round(average * factor * (0.5 + rnd.nextDouble()));
    }

    private double roundToHalfStar(double raw) {
        double clamped = Math.max(0.5, Math.min(5.0, raw));
        return Math.round(clamped * 2) / 2.0;
    }

    private String generateComment(double score) {
        String[] pool = score >= 4.5 ? COMMENTS_GOOD : score >= 3.0 ? COMMENTS_AVERAGE : COMMENTS_BAD;
        return pool[rnd.nextInt(pool.length)];
    }

    // ── Ad üretimi ────────────────────────────────────────────────────────────

    private String uniqueName(String category, String district, Set<String> used) {
        String[] cuisines = CUISINE_NAMES.get(category);
        for (int attempt = 0; attempt < 40; attempt++) {
            String on = rnd.nextBoolean() ? district : PREFIXES[rnd.nextInt(PREFIXES.length)];
            String name = on + " " + cuisines[rnd.nextInt(cuisines.length)];
            if (used.add(name)) return name;
        }
        String name = district + " " + cuisines[0] + " " + (used.size() + 1);
        used.add(name);
        return name;
    }

    /** Merkez ilçeler daha yoğun — gerçek dağılıma benzesin. */
    private int weightedDistrict() {
        double u = rnd.nextDouble();
        if (u < 0.45) return rnd.nextInt(5);        // ilk 5 ilçe: merkez
        if (u < 0.80) return 5 + rnd.nextInt(6);
        return 11 + rnd.nextInt(DISTRICTS.length - 11);
    }

    // ── Sabitler ──────────────────────────────────────────────────────────────

    private record District(String name, double lat, double lng) {}

    private static final District[] DISTRICTS = {
            new District("Kadıköy", 40.9903, 29.0270), new District("Beşiktaş", 41.0430, 29.0090),
            new District("Şişli", 41.0602, 28.9877), new District("Beyoğlu", 41.0350, 28.9770),
            new District("Üsküdar", 41.0233, 29.0152), new District("Fatih", 41.0186, 28.9400),
            new District("Bakırköy", 40.9820, 28.8720), new District("Ataşehir", 40.9920, 29.1270),
            new District("Maltepe", 40.9350, 29.1300), new District("Sarıyer", 41.1670, 29.0580),
            new District("Zeytinburnu", 40.9920, 28.9020), new District("Bağcılar", 41.0353, 28.8560),
            new District("Kartal", 40.8870, 29.1900), new District("Pendik", 40.8770, 29.2330),
            new District("Eyüpsultan", 41.0480, 28.9330), new District("Güngören", 41.0200, 28.8760),
            new District("Beylikdüzü", 41.0010, 28.6410), new District("Esenyurt", 41.0340, 28.6800),
    };

    private static final String[] CATEGORIES = {
            "Burger", "Pizza", "Türk Mutfağı", "Ev Yemeği", "Sushi", "Tatlı", "Kahvaltı",
            "İtalyan", "Vegan", "Meze", "Noodle", "Tavuk", "Sandviç"};

    private static final String[] SIDE_CATEGORIES = {"Tatlı", "Vegan", "Sandviç"};

    private static final String[] PREFIXES = {
            "Mavi", "Köşe", "Sokak", "Liman", "Bereket", "Keyif", "Nar", "Zeytin",
            "Ustam", "Dede", "Çınar", "Sahil", "Kubbe", "Lezzet", "Meydan", "Fırın"};

    private static final Map<String, String[]> CUISINE_NAMES = Map.ofEntries(
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
     * verinin kategorisi de bu tabloya göre düzeltilir ({@link #sync()}).
     * Fotoğraflar yemek adıyla eşleşir: yeni yemek eklersen
     * {@code mock/dish-photos.json}'a da ekle.
     */
    private static final Map<String, String[]> DISHES = Map.ofEntries(
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
    private static final Map<String, String> DISH_CATEGORY = new HashMap<>();

    static {
        DISHES.forEach((category, dishes) -> {
            for (String y : dishes) {
                String previous = DISH_CATEGORY.put(y, category);
                if (previous != null) {
                    throw new IllegalStateException(y + " iki kategoride: " + previous + ", " + category);
                }
            }
        });
    }

    private static final String[] FIRST_NAMES = {"Ahmet", "Ayşe", "Mehmet", "Elif", "Mustafa", "Zeynep",
            "Emre", "Fatma", "Burak", "Merve", "Can", "Selin", "Kerem", "Deniz", "Okan", "Ece",
            "Serkan", "Buse", "Onur", "İrem", "Tolga", "Ceren", "Barış", "Gizem"};
    private static final String[] LAST_NAMES = {"Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız",
            "Yıldırım", "Öztürk", "Aydın", "Özdemir", "Arslan", "Doğan", "Kılıç", "Aslan", "Çetin"};

    private static final String[] NEIGHBORHOODS = {"Caferağa", "Osmanağa", "Rasimpaşa", "Sinanpaşa",
            "Levent", "Etiler", "Cihangir", "Galata", "Moda", "Fenerbahçe", "Acıbadem", "Koşuyolu"};
    private static final String[] STREETS = {"Bağdat Cad.", "İstiklal Cad.", "Bahariye Cad.",
            "Halaskargazi Cad.", "Nispetiye Cad.", "Rumeli Cad.", "Moda Cad.", "Sahil Yolu"};

    private static final String[] COMMENTS_GOOD = {
            "Beklediğimden çok daha iyiydi, kesinlikle tekrar geleceğim.",
            "Porsiyon doyurucu, lezzet tam kıvamında.",
            "Buranın en iyi işi bu bence.", "Arkadaşlara da önerdim, hepsi beğendi.",
            "Fiyatına göre gerçekten başarılı.", "Sıcacık geldi, sunumu da güzeldi."};
    private static final String[] COMMENTS_AVERAGE = {
            "Fena değil ama bir daha söyler miyim bilmem.",
            "Lezzet iyiydi, porsiyon biraz küçük geldi.",
            "Ortalama. Beklentimi karşıladı sayılır.",
            "İyiydi ama fiyatı biraz yüksek.", "Gayet standart, sürprizi yok."};
    private static final String[] COMMENTS_BAD = {
            "Maalesef beğenmedim, soğuk geldi.",
            "Fiyatına göre kesinlikle değmez.",
            "Tadı beklediğim gibi değildi.", "Bir daha denemem açıkçası."};
}
