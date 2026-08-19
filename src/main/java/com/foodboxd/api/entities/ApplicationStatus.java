package com.foodboxd.api.entities;

public enum ApplicationStatus {
    PENDING,    // Başvuru yapıldı, inceleme bekliyor
    APPROVED,   // Onaylandı — restoran oluşturuldu, kullanıcı RESTAURANT_OWNER oldu
    REJECTED    // Reddedildi
}
