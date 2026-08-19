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

    // Sahiplik durumu — admin panelinde filtreleme için
    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_status", nullable = false, length = 20)
    @Builder.Default
    private RestaurantOwnershipStatus ownershipStatus = RestaurantOwnershipStatus.ADMIN_MANAGED;

    // Ortak sahiplik açık mı?
    // true → PRIMARY_OWNER, başkalarını ortak olarak davet edebilir
    // false → sadece PRIMARY_OWNER yönetir
    @Column(name = "co_ownership_enabled", nullable = false)
    @Builder.Default
    private boolean coOwnershipEnabled = false;

    // Bu restoranın sahipleri (PRIMARY_OWNER + CO_OWNER'lar)
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RestaurantOwner> owners;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MenuItem> menuItems;
}
