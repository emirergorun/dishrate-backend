package com.foodboxd.api.services.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Resend anahtarı yokken (geliştirme): mail gönderilmez, yalnız log'a
 * düşer. 2.7/2.8 akışları gerçek mail atmadan sınanabilir.
 */
@Slf4j
public class LogMailService implements MailService {

    @Override
    public void send(String to, String subject, String text, String html) {
        log.info("Mail gönderilmedi (Resend anahtarı yok, yalnız log): alıcı={}, konu=\"{}\"",
                MailService.mask(to), subject);
        // Geliştirmede bağlantıyı görebilmek için metin DEBUG seviyesinde.
        log.debug("Mail metni:\n{}", text);
    }
}
