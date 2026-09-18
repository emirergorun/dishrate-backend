package com.foodboxd.api.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Sahte veride yemek adına göre fotoğraf havuzu
 * ({@code resources/mock/dish-photos.json}).
 *
 * <p>Görseller Wikimedia Commons'tan, her yemek için elle seçildi; aynı
 * yemeğin farklı restoranlarda farklı fotoğrafı olsun diye çoğunda birden
 * fazla var. Commons görselleri atıf isteyen lisanslarla geliyor —
 * yalnızca geliştirme verisi içindir, yayında kullanılmamalı.
 */
@Slf4j
@Component
public class DishPhotos {

    private final Map<String, List<String>> pool;

    public DishPhotos(ObjectMapper objectMapper) {
        Map<String, List<String>> loaded;
        try (InputStream in = new ClassPathResource("mock/dish-photos.json").getInputStream()) {
            loaded = objectMapper.readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("Yemek fotoğrafı havuzu okunamadı: {}", e.getMessage());
            loaded = Map.of();
        }
        this.pool = loaded;
    }

    /** Yemeğin fotoğrafları; tanınmayan yemekte boş liste. */
    public List<String> photos(String dish) {
        return pool.getOrDefault(dish, List.of());
    }

    /**
     * Anahtara göre sabit bir fotoğraf seçer — aynı yemek aynı anahtarla hep
     * aynı fotoğrafı alır, farklı restoranlar farklı fotoğraf alır.
     */
    public String pick(String dish, long key) {
        List<String> l = photos(dish);
        return l.isEmpty() ? null : l.get((int) Math.floorMod(key, (long) l.size()));
    }
}
