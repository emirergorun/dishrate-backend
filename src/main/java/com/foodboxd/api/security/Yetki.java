package com.foodboxd.api.security;

import com.foodboxd.api.entities.User;
import org.springframework.security.access.AccessDeniedException;

/**
 * Kayıt bazlı yetki kontrolleri.
 *
 * Giriş yapmış olmak tek başına yetmez: adresteki ya da gövdedeki kullanıcı
 * kimliği, isteği atan kullanıcıyla aynı olmalı. Bu kontrol olmadan herkes
 * başkasının adına puan verebiliyor, başkasının kaydını silebiliyordu.
 */
public final class Yetki {

    private Yetki() {
    }

    /** İstek, adı geçen kullanıcının kendisinden gelmiyorsa 403. */
    public static void kendisi(User istekYapan, Long userId) {
        if (istekYapan == null || userId == null || !istekYapan.getUserId().equals(userId)) {
            throw new AccessDeniedException("Bu işlem yalnızca hesabın sahibi tarafından yapılabilir.");
        }
    }
}
