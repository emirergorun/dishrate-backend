package com.foodboxd.api.entities;

public enum RestaurantOwnershipStatus {

    /** Admin tarafından eklendi, henüz kimse sahiplik talebinde bulunmadı.
     *  Restoran aktif gözükür ama "Bu işletmenin sahibi misiniz?" butonu çıkar. */
    ADMIN_MANAGED,

    /** Onaylı bir PRIMARY_OWNER var. Co-owner olsa da olmasa da bu statü geçerlidir.
     *  Co-ownership detayı için co_ownership_enabled ve restaurant_owners tablosuna bakılır. */
    OWNER_MANAGED
}
