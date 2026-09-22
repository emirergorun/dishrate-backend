package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "menu_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"restaurant_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_item_id")
    private Long menuItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "average_rating", precision = 3, scale = 2, columnDefinition = "DECIMAL(3,2) DEFAULT 0.0")
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    /**
     * Kaç kişi puanladı. Ortalamanın yanında tutuluyor: restoran menüsünü
     * "en çok değerlendirilen"e göre sıralamak için her yemekte ayrı sayım
     * sorgusu atmak gerekmesin. {@code RatingService} ortalamayla birlikte
     * günceller; kolon sonradan eklendiği için eski satırları
     * {@code DataMigrator} doldurur.
     */
    @Column(name = "rating_count")
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Rating> ratings;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WishlistItem> wishlistItems;
}
