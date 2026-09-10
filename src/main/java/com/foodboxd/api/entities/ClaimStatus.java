package com.foodboxd.api.entities;

/** Sahiplik talebinin durumu. */
public enum ClaimStatus {
    PENDING,    // Talep alındı, admin incelemesi bekliyor
    APPROVED,   // Onaylandı — restaurants.owner_id atandı, kullanıcı owner oldu
    REJECTED    // Reddedildi
}
