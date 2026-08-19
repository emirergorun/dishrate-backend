package com.foodboxd.api.config;

import com.foodboxd.api.entities.Address;
import com.foodboxd.api.entities.Category;
import com.foodboxd.api.entities.MenuItem;
import com.foodboxd.api.entities.Restaurant;
import com.foodboxd.api.repositories.AddressRepository;
import com.foodboxd.api.repositories.CategoryRepository;
import com.foodboxd.api.repositories.MenuItemRepository;
import com.foodboxd.api.repositories.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Geliştirme/demo için örnek restoran + menü verisi tohumlar.
 * Sadece veritabanında hiç restoran yoksa çalışır (idempotent).
 * Üretimde `app.seed.data=false` ile kapatın → gerçek veriye dokunmaz.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final AddressRepository addressRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    @Value("${app.seed.data:true}")
    private boolean seedEnabled;

    // ── Fotoğraf URL'leri (Unsplash) ──────────────────────────────────────────
    private static final String BURGER1 = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400&h=300&fit=crop";
    private static final String BURGER2 = "https://images.unsplash.com/photo-1553979459-d2229ba7433b?w=400&h=300&fit=crop";
    private static final String BURGER3 = "https://images.unsplash.com/photo-1571091718767-18b5b1457add?w=400&h=300&fit=crop";
    private static final String BURGER4 = "https://images.unsplash.com/photo-1594212699903-ec8a3eca50f5?w=400&h=300&fit=crop";
    private static final String PIZZA1 = "https://images.unsplash.com/photo-1574071318508-1cdbab80d002?w=400&h=300&fit=crop";
    private static final String PIZZA2 = "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=400&h=300&fit=crop";
    private static final String SUSHI1 = "https://images.unsplash.com/photo-1617196034183-421b4040ed20?w=400&h=300&fit=crop";
    private static final String SUSHI2 = "https://images.unsplash.com/photo-1579584425555-c3ce17fd4351?w=400&h=300&fit=crop";
    private static final String KEBAP1 = "https://images.unsplash.com/photo-1599487488170-d11ec9c172f0?w=400&h=300&fit=crop";
    private static final String KEBAP2 = "https://images.unsplash.com/photo-1561651823-34feb02250e4?w=400&h=300&fit=crop";
    private static final String TAVUK = "https://images.unsplash.com/photo-1532550884612-72b92802ec04?w=400&h=300&fit=crop";
    private static final String KAHVALTI1 = "https://images.unsplash.com/photo-1608039829572-78524f79c4c7?w=400&h=300&fit=crop";
    private static final String KAHVALTI2 = "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=400&h=300&fit=crop";
    private static final String TATLI = "https://images.unsplash.com/photo-1519676867240-f03562e64548?w=400&h=300&fit=crop";
    private static final String ITALYAN1 = "https://images.unsplash.com/photo-1612874742237-6526221588e3?w=400&h=300&fit=crop";
    private static final String ITALYAN2 = "https://images.unsplash.com/photo-1476124369491-e7addf5db371?w=400&h=300&fit=crop";
    private static final String NOODLE = "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400&h=300&fit=crop";
    private static final String VEGAN1 = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=400&h=300&fit=crop";
    private static final String VEGAN2 = "https://images.unsplash.com/photo-1547592180-85f173990554?w=400&h=300&fit=crop";
    private static final String FRIES = "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=400&h=300&fit=crop";

    private final Map<String, Category> categories = new HashMap<>();

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("Örnek veri tohumlama kapalı (app.seed.data=false).");
            return;
        }
        if (restaurantRepository.count() > 0) {
            log.info("Örnek veri zaten mevcut, tohumlama atlandı.");
            return;
        }
        log.info("Örnek restoran/menü verisi tohumlanıyor...");

        // Kategoriler
        for (String c : new String[]{
                "Burger", "Pizza", "Kebap", "Sushi", "Tatlı", "Kahvaltı",
                "İtalyan", "Vegan", "Meze", "Noodle", "Tavuk", "Sandviç"}) {
            categories.put(c, categoryRepository.save(Category.builder().name(c).build()));
        }

        // ── Burger ──
        Restaurant r;
        r = restaurant("Bun Lab", "İstanbul", "Kadıköy", "Moda Cad. No:5, Kadıköy", 40.9907, 29.0262);
        item(r, "Burger", "Double Smash", 395, 4.9, BURGER2);
        item(r, "Burger", "Crispy Chicken Burger", 340, 4.7, BURGER1);
        item(r, "Burger", "Loaded Fries", 180, 4.6, FRIES);
        item(r, "Tatlı", "Oreo Shake", 150, 4.5, TATLI);

        r = restaurant("Smoke & Grill", "İstanbul", "Şişli", "Halaskargazi Cad. No:21, Şişli", 41.0602, 28.9877);
        item(r, "Burger", "Triple Smash", 420, 4.9, BURGER3);
        item(r, "Burger", "BBQ Bacon Burger", 380, 4.8, BURGER1);
        item(r, "Burger", "Smoke Ribs", 550, 4.7, BURGER4);
        item(r, "Burger", "Peynirli Patates", 160, 4.6, FRIES);

        r = restaurant("The Fat Cow", "İstanbul", "Bebek", "Bebek Cad. No:14, Bebek", 41.0774, 29.0396);
        item(r, "Burger", "Wagyu Burger", 650, 4.8, BURGER4);
        item(r, "Burger", "Truffle Fries", 220, 4.9, FRIES);
        item(r, "Vegan", "Burrata Salatası", 280, 4.7, VEGAN1);
        item(r, "Tatlı", "Çikolata Fondanı", 190, 4.6, TATLI);

        r = restaurant("Burger Joint", "İstanbul", "Beşiktaş", "Sinanpaşa Mah. No:8, Beşiktaş", 41.0422, 29.0056);
        item(r, "Burger", "Smash Burger", 320, 4.7, BURGER1);
        item(r, "Burger", "Jalapeno Burger", 350, 4.6, BURGER2);
        item(r, "Burger", "Mushroom Swiss Burger", 360, 4.5, BURGER3);
        item(r, "Burger", "Sweet Potato Fries", 140, 4.4, FRIES);

        // ── Pizza ──
        r = restaurant("Forno di Napoli", "İstanbul", "Galata", "Galata Kulesi Sok. No:3, Beyoğlu", 41.0256, 28.9744);
        item(r, "Pizza", "Pepperoni Calzone", 380, 4.7, PIZZA2);
        item(r, "Pizza", "Margherita DOC", 320, 4.8, PIZZA1);
        item(r, "Pizza", "Quattro Formaggi", 360, 4.6, PIZZA1);
        item(r, "Tatlı", "Tiramisu", 180, 4.9, TATLI);

        r = restaurant("Pizza Napoli", "İstanbul", "Nişantaşı", "Abdi İpekçi Cad. No:44, Nişantaşı", 41.0503, 28.9998);
        item(r, "Pizza", "Diavola", 350, 4.7, PIZZA1);
        item(r, "Pizza", "Prosciutto e Funghi", 370, 4.6, PIZZA2);
        item(r, "İtalyan", "Burrata Bruschetta", 220, 4.5, VEGAN1);
        item(r, "Tatlı", "Panna Cotta", 160, 4.4, TATLI);

        // ── Sushi ──
        r = restaurant("Sushi Kaito", "İstanbul", "Nişantaşı", "Teşvikiye Cad. No:18, Nişantaşı", 41.0492, 29.0008);
        item(r, "Sushi", "Omakase Set (10 pcs)", 1200, 5.0, SUSHI2);
        item(r, "Sushi", "Salmon Nigiri (8 adet)", 480, 4.8, SUSHI1);
        item(r, "Sushi", "Dragon Roll", 520, 4.9, SUSHI1);
        item(r, "Noodle", "Miso Çorbası", 120, 4.5, VEGAN2);

        r = restaurant("Nobu Istanbul", "İstanbul", "Etiler", "Nispetiye Cad. No:76, Etiler", 41.0794, 29.0235);
        item(r, "Sushi", "Tuna Tataki", 480, 4.6, SUSHI1);
        item(r, "Sushi", "Black Cod Miso", 750, 4.9, SUSHI2);
        item(r, "Sushi", "Yellowtail Jalapeño", 620, 4.8, SUSHI1);
        item(r, "Sushi", "Wagyu Gyoza", 560, 4.7, SUSHI2);

        // ── Kebap ──
        r = restaurant("Ocakbaşı 1969", "İstanbul", "Karaköy", "Kemankeş Cad. No:11, Karaköy", 41.0231, 28.9766);
        item(r, "Kebap", "Adana Kebap", 350, 4.8, KEBAP1);
        item(r, "Kebap", "Urfa Kebap", 340, 4.7, KEBAP1);
        item(r, "Kebap", "Kuzu Şiş", 420, 4.9, KEBAP1);
        item(r, "Kebap", "Patlıcan Kebabı", 380, 4.6, KEBAP1);

        r = restaurant("Karadeniz Döner", "İstanbul", "Beşiktaş", "Barbaros Blv. No:3, Beşiktaş", 41.0430, 29.0030);
        item(r, "Kebap", "Dürüm Döner", 120, 4.5, KEBAP2);
        item(r, "Kebap", "Ekmek Arası Döner", 100, 4.4, KEBAP2);
        item(r, "Kebap", "Yarım Porsiyon", 80, 4.3, KEBAP2);
        item(r, "Tavuk", "Tavuk Dürüm", 110, 4.4, TAVUK);

        r = restaurant("Mersin Tantunisi", "İstanbul", "Bağcılar", "Fevzi Paşa Cad. No:55, Bağcılar", 41.0353, 28.8560);
        item(r, "Kebap", "Tantuni", 180, 4.9, KEBAP2);
        item(r, "Kebap", "Dürüm Tantuni", 200, 4.8, KEBAP2);
        item(r, "Kebap", "Acılı Tantuni", 190, 4.7, KEBAP1);
        item(r, "Vegan", "Şalgam Suyu", 30, 4.5, VEGAN2);

        // ── Tavuk ──
        r = restaurant("Tavukçu Mehmet", "İstanbul", "Kadıköy", "Söğütlüçeşme Cad. No:22, Kadıköy", 40.9920, 29.0284);
        item(r, "Tavuk", "Izgara Tavuk", 280, 4.7, TAVUK);
        item(r, "Tavuk", "Tavuk Şiş", 260, 4.6, TAVUK);
        item(r, "Tavuk", "Kanat (8 adet)", 240, 4.8, TAVUK);
        item(r, "Tavuk", "Pilav Üstü Tavuk", 220, 4.5, TAVUK);

        r = restaurant("Pilav Evi", "İstanbul", "Fatih", "Millet Cad. No:9, Fatih", 41.0198, 28.9397);
        item(r, "Tavuk", "Tavuklu Pilav", 130, 4.6, TAVUK);
        item(r, "Tavuk", "İzgara + Pilav", 200, 4.5, TAVUK);
        item(r, "Vegan", "Mercimek Çorbası", 80, 4.8, VEGAN2);
        item(r, "Tatlı", "Sütlaç", 90, 4.7, TATLI);

        // ── Kahvaltı ──
        r = restaurant("Sunday Brunch", "İstanbul", "Moda", "Moda Cad. No:42, Kadıköy", 40.9877, 29.0290);
        item(r, "Kahvaltı", "Eggs Benedict", 290, 4.7, KAHVALTI1);
        item(r, "Kahvaltı", "Avocado Toast", 220, 4.5, KAHVALTI2);
        item(r, "Tatlı", "French Pancakes", 250, 4.8, TATLI);
        item(r, "Kahvaltı", "Granola Bowl", 200, 4.4, VEGAN1);

        r = restaurant("Gözlemeci Hanım", "İstanbul", "Üsküdar", "Hakimiyeti Milliye Cad. No:7, Üsküdar", 41.0234, 29.0152);
        item(r, "Kahvaltı", "Karışık Gözleme", 95, 4.7, KAHVALTI1);
        item(r, "Kahvaltı", "Peynirli Gözleme", 80, 4.6, KAHVALTI1);
        item(r, "Vegan", "Ispanaklı Gözleme", 85, 4.5, VEGAN1);
        item(r, "Kahvaltı", "Çay", 15, 4.9, KAHVALTI2);

        // ── Tatlı ──
        r = restaurant("Güllüoğlu", "İstanbul", "Karaköy", "Rıhtım Cad. No:3, Karaköy", 41.0240, 28.9770);
        item(r, "Tatlı", "Fıstıklı Baklava", 120, 4.9, TATLI);
        item(r, "Tatlı", "Sütlü Nuriye", 100, 4.8, TATLI);
        item(r, "Tatlı", "Cevizli Baklava", 110, 4.7, TATLI);
        item(r, "Tatlı", "Burma Kadayıf", 115, 4.8, TATLI);

        r = restaurant("Şanlıurfa Sofrası", "İstanbul", "Fatih", "Ordu Cad. No:12, Fatih", 41.0188, 28.9410);
        item(r, "Tatlı", "Künefe", 180, 4.9, TATLI);
        item(r, "Tatlı", "Sütlü Künefe", 190, 4.8, TATLI);
        item(r, "Kebap", "Adana Kebap", 320, 4.7, KEBAP1);
        item(r, "Vegan", "Ayran", 25, 4.5, VEGAN2);

        // ── İtalyan ──
        r = restaurant("La Cucina", "İstanbul", "Cihangir", "Cihangir Cad. No:29, Beyoğlu", 41.0330, 28.9820);
        item(r, "İtalyan", "Truffle Risotto", 520, 4.8, ITALYAN2);
        item(r, "İtalyan", "Truffle Carbonara", 460, 4.8, ITALYAN1);
        item(r, "İtalyan", "Burrata & Pomodoro", 380, 4.7, VEGAN1);
        item(r, "Tatlı", "Tiramisu della Casa", 240, 4.9, TATLI);

        // ── Noodle ──
        r = restaurant("Noodle Bar", "İstanbul", "Karaköy", "Tersane Cad. No:6, Karaköy", 41.0215, 28.9758);
        item(r, "Noodle", "Ramen", 320, 4.8, NOODLE);
        item(r, "Noodle", "Spicy Miso Ramen", 340, 4.7, NOODLE);
        item(r, "Noodle", "Pad Thai", 300, 4.6, NOODLE);
        item(r, "Noodle", "Gyoza (6 adet)", 180, 4.5, SUSHI1);

        // ── Vegan ──
        r = restaurant("Green Bowl", "İstanbul", "Nişantaşı", "Maçka Cad. No:15, Nişantaşı", 41.0490, 28.9988);
        item(r, "Vegan", "Quinoa Power Bowl", 280, 4.6, VEGAN1);
        item(r, "Vegan", "Açık Avocado Sandviç", 220, 4.5, KAHVALTI2);
        item(r, "Vegan", "Mercimek Köftesi", 160, 4.7, VEGAN2);
        item(r, "Vegan", "Chia Pudding", 140, 4.4, TATLI);

        r = restaurant("Earthly Kitchen", "İstanbul", "Beyoğlu", "İstiklal Cad. No:82, Beyoğlu", 41.0357, 28.9769);
        item(r, "Vegan", "Vegan Burger", 260, 4.5, BURGER1);
        item(r, "Vegan", "Buddha Bowl", 290, 4.6, VEGAN1);
        item(r, "Vegan", "Falafel Tabağı", 200, 4.7, VEGAN2);
        item(r, "Vegan", "Raw Cheesecake", 170, 4.4, TATLI);

        // ── Meze ──
        r = restaurant("Sahil Meyhane", "İstanbul", "Ortaköy", "Muallim Naci Cad. No:4, Ortaköy", 41.0530, 29.0289);
        item(r, "Meze", "Midye Dolma (12 adet)", 250, 4.7, SUSHI1);
        item(r, "Meze", "Enginar Zeytinyağlı", 180, 4.6, VEGAN1);
        item(r, "Meze", "Balık Tava", 420, 4.8, SUSHI2);
        item(r, "Meze", "Cacık", 90, 4.5, VEGAN2);

        r = restaurant("Kırım Mutfağı", "İstanbul", "Sarıyer", "Büyükdere Cad. No:33, Sarıyer", 41.1668, 29.0582);
        item(r, "Meze", "Çiğ Börek", 140, 4.8, KAHVALTI1);
        item(r, "Meze", "Çerkes Tavuğu", 280, 4.7, TAVUK);
        item(r, "Meze", "Hamur Kızartması", 120, 4.6, KAHVALTI2);
        item(r, "Meze", "Elma Kompostosu", 60, 4.4, TATLI);

        log.info("Tohumlama tamamlandı: {} restoran, {} menü öğesi.",
                restaurantRepository.count(), menuItemRepository.count());
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private Restaurant restaurant(String name, String city, String district,
                                  String fullAddress, double lat, double lng) {
        Address address = addressRepository.save(Address.builder()
                .city(city)
                .district(district)
                .fullAddress(fullAddress)
                .latitude(lat)
                .longitude(lng)
                .build());
        return restaurantRepository.save(Restaurant.builder()
                .name(name)
                .address(address)
                .build());
    }

    private void item(Restaurant restaurant, String categoryName, String name,
                      double price, double rating, String photoUrl) {
        menuItemRepository.save(MenuItem.builder()
                .restaurant(restaurant)
                .category(categories.get(categoryName))
                .name(name)
                .price(BigDecimal.valueOf(price))
                .averageRating(BigDecimal.valueOf(rating))
                .photoUrl(photoUrl)
                .build());
    }
}
