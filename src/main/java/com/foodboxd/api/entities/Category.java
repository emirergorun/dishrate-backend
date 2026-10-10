package com.foodboxd.api.entities;

import com.foodboxd.api.utils.CategorySlug;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Değişmeyen anahtar ("turk-mutfagi"); ad değişse de aynı kalır (2.10b).
     * Kolon sonradan eklendiği için veritabanında boş geçebilir; boş kalan
     * satırları {@code DataMigrator} doldurur. Yeni kategori
     * {@code CategorySlug.of} ile anahtar alır.
     */
    @Column(name = "slug", unique = true, length = 60)
    private String slug;

    /** Çiplerin sırası (küçük önce). */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** Anahtarı addan üretilmiş yeni kategori (tohumlayıcılar için). */
    public static Category create(String name, Integer sortOrder) {
        return Category.builder()
                .name(name)
                .slug(CategorySlug.of(name))
                .sortOrder(sortOrder)
                .build();
    }
}
