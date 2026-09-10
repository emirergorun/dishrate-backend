package com.foodboxd.api.entities;

/**
 * Kullanıcı rolleri — artan yetki sırasıyla.
 *
 * <p>Roller birikimlidir: bir kullanıcı owner olduğunda hesabı korunur,
 * yalnızca rolü yükselir. Owner hâlâ yemek puanlar, istek listesi tutar;
 * üstüne kendi restoranını yönetme yetkisi eklenir.
 */
public enum UserRole {

    /** Normal kullanıcı — değerlendirir, istek listesi tutar. */
    USER(0),

    /** Onaylı restoran sahibi — USER'ın her şeyi + kendi restoranının yönetimi. */
    RESTAURANT_OWNER(1),

    /** Yönetici — her şeye erişebilir. */
    ADMIN(2);

    private final int level;

    UserRole(int level) {
        this.level = level;
    }

    /**
     * Bu rol, verilen rolün yetkilerini kapsıyor mu?
     *
     * <p>Kaba bir kapı: "owner mısın" sorusunu cevaplar, "bu restoranın
     * sahibi misin" sorusunu değil. İkincisi için restoran bazlı kontrol
     * gerekir (bkz. {@code RestaurantService.assertCanManage}).
     */
    public boolean atLeast(UserRole required) {
        return this.level >= required.level;
    }
}
