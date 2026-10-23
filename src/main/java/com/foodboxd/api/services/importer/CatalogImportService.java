package com.foodboxd.api.services.importer;

import com.foodboxd.api.entities.Address;
import com.foodboxd.api.entities.Category;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.repositories.AddressRepository;
import com.foodboxd.api.repositories.CategoryRepository;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import com.foodboxd.api.services.FileStorageService;
import com.foodboxd.api.services.importer.CatalogCsvReader.Column;
import com.foodboxd.api.services.importer.CatalogCsvReader.Row;
import com.foodboxd.api.services.importer.CatalogImportReport.Counts;
import com.foodboxd.api.services.importer.CatalogImportReport.Issue;
import com.foodboxd.api.utils.AddressFormatter;
import com.foodboxd.api.utils.SearchText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tablodan içerik içe aktarma (2.11b): restoranlar, menüleri ve
 * fotoğrafları bir CSV'den (+ fotoğraf zip'i) veritabanına.
 *
 * <p>Kurallar: önce bütün satırlar denetlenir; tek bir hata bile varsa hiçbir
 * şey yazılmaz. Tekrar çalıştırılabilir — restoran ad + il + ilçeyle, yemek
 * restoran + adla eşleşir (Türkçe karakter ve harf büyüklüğü fark etmez);
 * var olan güncellenir, olmayan eklenir. Tabloda olmayan yemek silinmez
 * (uyarı). Puan, yorum ve istek listelerine dokunulmaz. Kayıtlar gerçek veri
 * sayılır: {@code seedTag} boş, sahipsiz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogImportService {

    static final int MAX_ROWS = 2000;
    static final long MAX_CSV_BYTES = 2L * 1024 * 1024;

    /** Türkiye'nin kabaca sınırları; Google Maps'ten yanlış yer kopyalanmasın. */
    private static final double MIN_LAT = 35.8, MAX_LAT = 42.2, MIN_LNG = 25.6, MAX_LNG = 44.9;

    /** "41.0123, 29.0234" — Google Maps'in sağ tık kopyası; boşluk ya da noktalı virgül de olur. */
    private static final Pattern LOCATION =
            Pattern.compile("^\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*[,;\\s]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$");

    private final RestaurantRepository restaurantRepository;
    private final AddressRepository addressRepository;
    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final TurkeyAddresses turkeyAddresses;

    /** Denetlenmiş bir satır. */
    private record Line(int line, String restaurant, String province, String district,
                        String neighbourhood, String address, double lat, double lng,
                        String dish, Category category, String photoKey) {}

    /** Bir restoranın satırları. */
    private record Group(Line first, List<Line> dishes) {}

    /**
     * @param filesBaseUrl {@code https://alan/api/v1} — fotoğraf adresinin başı
     *                     ({@code FileController} ile aynı biçim)
     */
    @Transactional
    public CatalogImportReport run(MultipartFile csv, MultipartFile zip, boolean dryRun,
                                   String filesBaseUrl) {
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();

        if (csv == null || csv.isEmpty()) {
            throw new IllegalArgumentException("Tablo (CSV) dosyası boş.");
        }
        if (csv.getSize() > MAX_CSV_BYTES) {
            throw new IllegalArgumentException("Tablo en fazla 2 MB olabilir.");
        }

        CatalogCsvReader.Result parsed;
        try {
            parsed = CatalogCsvReader.read(csv.getBytes());
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new IllegalArgumentException("Tablo okunamadı; CSV olarak kaydedildiğinden emin ol.");
        }
        if (!parsed.missingColumns().isEmpty()) {
            errors.add(new Issue(1, null, "Eksik sütun: " + String.join(", ", parsed.missingColumns())
                    + ". Başlık satırı: " + headers()));
            return report(dryRun, false, 0, empty(), empty(), 0, errors, warnings);
        }
        List<Row> rows = parsed.rows();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Tabloda başlıktan başka satır yok.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Tek seferde en fazla " + MAX_ROWS + " satır aktarılabilir.");
        }

        Map<String, byte[]> zipFiles = Map.of();
        if (zip != null && !zip.isEmpty()) {
            try {
                zipFiles = ImportPhotos.readZip(zip.getInputStream());
            } catch (IOException e) {
                throw new IllegalArgumentException("Fotoğraf zip'i açılamadı.");
            }
        }

        // ── Aynı restoranın satırları tutarlı mı (ham değerlerle: bir satır
        //    başka bir sebeple hatalı olsa da çelişki aynı turda görünsün) ──
        Map<String, Row> firstRow = new HashMap<>();
        Set<Integer> conflicting = new HashSet<>();
        for (Row r : rows) {
            if (r.get(Column.RESTAURANT).isEmpty()) continue;
            String key = restaurantKey(r.get(Column.RESTAURANT), r.get(Column.PROVINCE), r.get(Column.DISTRICT));
            Row f = firstRow.putIfAbsent(key, r);
            if (f != null && !sameRestaurantInfo(f, r)) {
                errors.add(new Issue(r.line(), null, "Aynı restoranın " + f.line()
                        + ". satırıyla adres, mahalle ya da konum farklı; restoran bilgisi her satırda aynı olmalı."));
                conflicting.add(r.line());
            }
        }

        // ── Satırları denetle ────────────────────────────────────────────────
        Map<String, Category> categories = categoryIndex();
        List<Line> lines = new ArrayList<>();
        for (Row r : rows) {
            Line l = validate(r, categories, zipFiles, zip != null && !zip.isEmpty(), errors, warnings);
            if (l != null && !conflicting.contains(r.line())) lines.add(l);
        }

        // ── Restoranlara böl, tutarlılık ve tekrar ───────────────────────────
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Line l : lines) {
            String key = restaurantKey(l.restaurant(), l.province(), l.district());
            Group g = groups.get(key);
            if (g == null) {
                groups.put(key, new Group(l, new ArrayList<>(List.of(l))));
                continue;
            }
            boolean duplicate = g.dishes().stream()
                    .anyMatch(d -> SearchText.normalize(d.dish()).equals(SearchText.normalize(l.dish())));
            if (duplicate) {
                errors.add(new Issue(l.line(), Column.DISH.header,
                        "'" + l.dish() + "' bu restoranda iki kez yazılmış."));
                continue;
            }
            g.dishes().add(l);
        }

        // ── Fotoğrafları hazırla (denetim için önizlemede de) ────────────────
        Map<String, ImportPhotos.Prepared> prepared = new HashMap<>();
        for (Line l : lines) {
            if (l.photoKey() == null || prepared.containsKey(l.photoKey())) continue;
            try {
                prepared.put(l.photoKey(), ImportPhotos.prepare(zipFiles.get(l.photoKey())));
            } catch (IllegalArgumentException e) {
                errors.add(new Issue(l.line(), Column.PHOTO.header, e.getMessage()));
            }
        }
        if ((zip == null || zip.isEmpty()) && rows.stream().anyMatch(r -> !r.get(Column.PHOTO).isEmpty())) {
            warnings.add(new Issue(0, Column.PHOTO.header,
                    "Fotoğraf zip'i yüklenmedi; fotoğraflara dokunulmadı."));
        }
        Set<String> used = rows.stream().map(r -> r.get(Column.PHOTO)).filter(p -> !p.isEmpty())
                .map(ImportPhotos::key).collect(Collectors.toSet());
        List<String> unused = zipFiles.keySet().stream().filter(k -> !used.contains(k)).sorted().toList();
        if (!unused.isEmpty()) {
            warnings.add(new Issue(0, Column.PHOTO.header,
                    "Zip'te tabloda geçmeyen dosyalar (kullanılmadı): " + String.join(", ", unused)));
        }

        // ── Veritabanıyla karşılaştır ────────────────────────────────────────
        Map<String, Restaurant> existing = existingRestaurants(groups.values());
        int rCreated = 0, rUpdated = 0, rUnchanged = 0, iCreated = 0, iUpdated = 0, iUnchanged = 0;
        int photosToStore = 0;
        Map<Long, List<MenuItem>> menus = new HashMap<>();
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            Restaurant r = existing.get(e.getKey());
            if (r == null) {
                rCreated++;
                iCreated += g.dishes().size();
                photosToStore += (int) g.dishes().stream().filter(d -> d.photoKey() != null).count();
                continue;
            }
            if (restaurantDiffers(r, g.first())) rUpdated++; else rUnchanged++;
            List<MenuItem> menu = menuItemRepository.findByRestaurant_RestaurantId(r.getRestaurantId());
            menus.put(r.getRestaurantId(), menu);
            Map<String, MenuItem> byName = menu.stream()
                    .collect(Collectors.toMap(m -> SearchText.normalize(m.getName()), m -> m, (a, b) -> a));
            Set<String> inSheet = new HashSet<>();
            for (Line d : g.dishes()) {
                String key = SearchText.normalize(d.dish());
                inSheet.add(key);
                MenuItem m = byName.get(key);
                String url = photoUrl(prepared.get(d.photoKey()), filesBaseUrl);
                if (m == null) {
                    iCreated++;
                    if (url != null) photosToStore++;
                } else if (itemDiffers(m, d, url)) {
                    iUpdated++;
                    if (url != null && !url.equals(m.getPhotoUrl())) photosToStore++;
                } else {
                    iUnchanged++;
                }
            }
            List<String> notInSheet = menu.stream()
                    .filter(m -> !inSheet.contains(SearchText.normalize(m.getName())))
                    .map(MenuItem::getName).sorted().toList();
            if (!notInSheet.isEmpty()) {
                warnings.add(new Issue(g.first().line(), Column.DISH.header, "'" + r.getName()
                        + "' menüsünde olup tabloda olmayan yemekler silinmedi: " + String.join(", ", notInSheet)));
            }
        }

        Counts restaurants = new Counts(rCreated, rUpdated, rUnchanged);
        Counts items = new Counts(iCreated, iUpdated, iUnchanged);
        if (!errors.isEmpty() || dryRun) {
            return report(dryRun, false, rows.size(), restaurants, items, photosToStore, errors, warnings);
        }

        // ── Yaz ──────────────────────────────────────────────────────────────
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            Restaurant r = existing.get(e.getKey());
            if (r == null) {
                r = createRestaurant(g.first());
                menus.put(r.getRestaurantId(), new ArrayList<>());
            } else {
                updateRestaurant(r, g.first());
            }
            Map<String, MenuItem> byName = menus.get(r.getRestaurantId()).stream()
                    .collect(Collectors.toMap(m -> SearchText.normalize(m.getName()), m -> m, (a, b) -> a));
            for (Line d : g.dishes()) {
                ImportPhotos.Prepared photo = prepared.get(d.photoKey());
                String url = null;
                if (photo != null) {
                    String name = fileStorageService.storeJpeg(photo.jpeg(), photo.baseName());
                    url = filesBaseUrl + "/files/" + name;
                }
                MenuItem m = byName.get(SearchText.normalize(d.dish()));
                if (m == null) {
                    menuItemRepository.save(MenuItem.builder()
                            .restaurant(r)
                            .name(d.dish())
                            .category(d.category())
                            .photoUrl(url)
                            .build());
                } else if (itemDiffers(m, d, url)) {
                    m.setName(d.dish());
                    m.setCategory(d.category());
                    if (url != null) m.setPhotoUrl(url);
                    menuItemRepository.save(m);
                }
            }
        }
        log.info("İçe aktarma: restoran +{} ~{} ={}, yemek +{} ~{} ={}, fotoğraf {}",
                rCreated, rUpdated, rUnchanged, iCreated, iUpdated, iUnchanged, photosToStore);
        return report(false, true, rows.size(), restaurants, items, photosToStore, errors, warnings);
    }

    // ── Satır denetimi ───────────────────────────────────────────────────────

    private Line validate(Row r, Map<String, Category> categories, Map<String, byte[]> zipFiles,
                          boolean hasZip, List<Issue> errors, List<Issue> warnings) {
        int before = errors.size();
        int n = r.line();
        for (Column c : Column.values()) {
            if (c.required && r.get(c).isEmpty()) {
                errors.add(new Issue(n, c.header, "Boş bırakılmış."));
            }
        }
        String restaurant = r.get(Column.RESTAURANT);
        String dish = r.get(Column.DISH);
        String address = r.get(Column.ADDRESS);
        length(n, Column.RESTAURANT, restaurant, 255, errors);
        length(n, Column.DISH, dish, 255, errors);
        length(n, Column.ADDRESS, address, 255, errors);

        String province = null, district = null, neighbourhood = null;
        if (!r.get(Column.PROVINCE).isEmpty()) {
            province = turkeyAddresses.province(r.get(Column.PROVINCE)).orElse(null);
            if (province == null) {
                errors.add(new Issue(n, Column.PROVINCE.header, "'" + r.get(Column.PROVINCE) + "' diye bir il yok."));
            }
        }
        if (province != null && !r.get(Column.DISTRICT).isEmpty()) {
            district = turkeyAddresses.district(province, r.get(Column.DISTRICT)).orElse(null);
            if (district == null) {
                errors.add(new Issue(n, Column.DISTRICT.header,
                        "'" + r.get(Column.DISTRICT) + "' " + province + "'da bir ilçe değil."));
            }
        }
        String hood = r.get(Column.NEIGHBOURHOOD);
        if (district != null && !hood.isEmpty()) {
            neighbourhood = turkeyAddresses.neighbourhood(province, district, hood).orElse(null);
            if (neighbourhood == null) {
                // Mahalle listesi eksik olabilir; yazıldığı gibi alınır.
                neighbourhood = hood.replaceAll("(?i)\\s*(mahallesi|mah\\.?)$", "").trim();
                warnings.add(new Issue(n, Column.NEIGHBOURHOOD.header,
                        "'" + hood + "' " + district + " mahalle listesinde yok; yazıldığı gibi alındı."));
            }
            length(n, Column.NEIGHBOURHOOD, neighbourhood, 120, errors);
        }

        double lat = 0, lng = 0;
        String loc = r.get(Column.LOCATION);
        if (!loc.isEmpty()) {
            Matcher m = LOCATION.matcher(loc);
            if (!m.matches()) {
                errors.add(new Issue(n, Column.LOCATION.header,
                        "'" + loc + "' anlaşılmadı; Google Maps'ten kopyalanan \"41.0123, 29.0234\" biçimi olmalı."));
            } else {
                lat = Double.parseDouble(m.group(1));
                lng = Double.parseDouble(m.group(2));
                if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
                    errors.add(new Issue(n, Column.LOCATION.header,
                            "Konum Türkiye'nin dışında; enlem ve boylam yer değiştirmiş olabilir."));
                }
            }
        }

        Category category = null;
        String cat = r.get(Column.CATEGORY);
        if (!cat.isEmpty()) {
            category = categories.get(cat.trim());
            if (category == null) category = categories.get(SearchText.normalize(cat));
            if (category == null) {
                errors.add(new Issue(n, Column.CATEGORY.header, "'" + cat + "' diye bir kategori yok. Olanlar: "
                        + categoryNames()));
            }
        }

        String photoKey = null;
        String photo = r.get(Column.PHOTO);
        // Zip yüklenmediyse fotoğraf sütunu yok sayılır (yalnız metni
        // güncellemek için zip'i yeniden yüklemek gerekmesin); uyarı bir kez.
        if (!photo.isEmpty() && hasZip) {
            photoKey = ImportPhotos.key(photo);
            if (!ImportPhotos.supported(photo)) {
                errors.add(new Issue(n, Column.PHOTO.header,
                        "'" + photo + "' JPEG ya da PNG değil (.jpg, .jpeg, .png)."));
            } else if (!zipFiles.containsKey(photoKey)) {
                errors.add(new Issue(n, Column.PHOTO.header, "'" + photo + "' zip'te yok."));
            }
        }

        if (errors.size() > before) return null;
        return new Line(n, restaurant, province, district, neighbourhood, address, lat, lng,
                dish, category, photoKey);
    }

    private static void length(int n, Column c, String value, int max, List<Issue> errors) {
        if (value != null && value.length() > max) {
            errors.add(new Issue(n, c.header, "En fazla " + max + " karakter olabilir."));
        }
    }

    // ── Veritabanı ───────────────────────────────────────────────────────────

    private Map<String, Category> categoryIndex() {
        Map<String, Category> index = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            if (c.getSlug() != null) index.put(c.getSlug(), c);
            index.put(SearchText.normalize(c.getName()), c);
        }
        // Birleşen ya da yeniden adlanan kategorilerin eski adları (22–23 Ekim).
        Map<String, String> aliases = Map.of("pide", "pide-lahmacun", "lahmacun", "pide-lahmacun",
                "noodle", "asya");
        aliases.forEach((alias, slug) -> {
            Category target = index.get(slug);
            if (target != null) index.putIfAbsent(alias, target);
        });
        return index;
    }

    private String categoryNames() {
        return categoryRepository.findAllOrdered().stream().map(Category::getName)
                .collect(Collectors.joining(", "));
    }

    private Map<String, Restaurant> existingRestaurants(Iterable<Group> groups) {
        Set<String> provinces = new HashSet<>();
        for (Group g : groups) provinces.add(g.first().province());
        Map<String, Restaurant> map = new HashMap<>();
        for (String p : provinces) {
            for (Restaurant r : restaurantRepository.findByAddress_City(p)) {
                Address a = r.getAddress();
                map.putIfAbsent(restaurantKey(r.getName(), a.getCity(), a.getDistrict()), r);
            }
        }
        return map;
    }

    private static String restaurantKey(String name, String province, String district) {
        return SearchText.normalize(name) + "|" + SearchText.normalize(province) + "|"
                + SearchText.normalize(district == null ? "" : district);
    }

    /** İki satırdaki restoran bilgisi aynı mı (adres, mahalle, konum; boşluk ve harf farkı sayılmaz). */
    private static boolean sameRestaurantInfo(Row a, Row b) {
        for (Column c : List.of(Column.ADDRESS, Column.NEIGHBOURHOOD, Column.LOCATION)) {
            if (!SearchText.normalize(a.get(c)).equals(SearchText.normalize(b.get(c)))) return false;
        }
        return true;
    }

    private static boolean restaurantDiffers(Restaurant r, Line l) {
        Address a = r.getAddress();
        return !r.getName().equals(l.restaurant())
                || !Objects.equals(a.getAddressLine1(), l.address())
                || !Objects.equals(a.getNeighbourhood(), l.neighbourhood())
                || !Objects.equals(a.getLatitude(), l.lat())
                || !Objects.equals(a.getLongitude(), l.lng());
    }

    private static boolean itemDiffers(MenuItem m, Line d, String photoUrl) {
        Long current = m.getCategory() == null ? null : m.getCategory().getCategoryId();
        return !m.getName().equals(d.dish())
                || !Objects.equals(current, d.category().getCategoryId())
                || (photoUrl != null && !photoUrl.equals(m.getPhotoUrl()));
    }

    private Restaurant createRestaurant(Line l) {
        Address address = addressRepository.save(Address.builder()
                .city(l.province())
                .district(l.district())
                .neighbourhood(l.neighbourhood())
                .addressLine1(l.address())
                .fullAddress(fullAddress(l))
                .latitude(l.lat())
                .longitude(l.lng())
                .build());
        return restaurantRepository.save(Restaurant.builder()
                .name(l.restaurant())
                .address(address)
                .build());
    }

    private void updateRestaurant(Restaurant r, Line l) {
        if (!restaurantDiffers(r, l)) return;
        r.setName(l.restaurant());
        Address a = r.getAddress();
        a.setNeighbourhood(l.neighbourhood());
        a.setAddressLine1(l.address());
        a.setFullAddress(fullAddress(l));
        a.setLatitude(l.lat());
        a.setLongitude(l.lng());
        addressRepository.save(a);
        restaurantRepository.save(r);
    }

    private static String fullAddress(Line l) {
        return AddressFormatter.compose(l.neighbourhood(), l.address(), null, null, null, null,
                l.district(), l.province());
    }

    private static String photoUrl(ImportPhotos.Prepared p, String base) {
        return p == null ? null : base + "/files/" + p.baseName() + ".jpg";
    }

    private static String headers() {
        List<String> h = new ArrayList<>();
        for (Column c : Column.values()) h.add(c.header);
        return String.join(", ", h);
    }

    private static Counts empty() {
        return new Counts(0, 0, 0);
    }

    private static CatalogImportReport report(boolean dryRun, boolean applied, int rows, Counts r, Counts i,
                                              int photos, List<Issue> errors, List<Issue> warnings) {
        // Satır sırasıyla (aynı satırda sütun sırası korunur).
        List<Issue> e = new ArrayList<>(errors);
        List<Issue> w = new ArrayList<>(warnings);
        e.sort(java.util.Comparator.comparingInt(Issue::row));
        w.sort(java.util.Comparator.comparingInt(Issue::row));
        return new CatalogImportReport(dryRun, applied, rows, r, i, photos, e, w);
    }
}
