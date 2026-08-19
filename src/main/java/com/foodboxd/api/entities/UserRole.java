package com.foodboxd.api.entities;

public enum UserRole {
    USER,               // Normal kullanıcı — değerlendirir, istek listesi tutar
    RESTAURANT_OWNER,   // Onaylı restoran sahibi — kendi menüsünü yönetir
    ADMIN               // Yönetici — her şeye erişebilir
}
