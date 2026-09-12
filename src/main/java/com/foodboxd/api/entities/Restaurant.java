package com.foodboxd.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /**
     * Restoranın sahibi. Sahipliğin <b>tek</b> kaynağı burasıdır.
     *
     * <p>null → restoran sahipsiz; katalogda görünür, puanlanabilir ve
     * sahiplik talebine açıktır. Dolu → sahibi var; ikinci bir talep kabul
     * edilmez.
     *
     * <p>Yalnızca {@code ClaimService} onay akışı bu alanı doldurur.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /**
     * Kaydın nereden geldiği. Şu an tek değer: {@code "mock"} — geliştirme için
     * üretilen sahte veri. Gerçek kayıtlarda null.
     *
     * <p>Sahte veriyi tek komutla ve <b>yalnızca onu</b> silebilmek için var
     * ({@code MockDataSeeder}); kendi hesabın ve gerçek kayıtlar etkilenmez.
     */
    @Column(name = "seed_tag", length = 20)
    private String seedTag;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MenuItem> menuItems;

    /** Sahiplik talebine açık mı? */
    public boolean isClaimable() {
        return owner == null;
    }

    /** Verilen kullanıcı bu restoranın sahibi mi? */
    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.getUserId().equals(userId);
    }
}
