package com.foodboxd.api.services.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deneme maili (2.6): {@code --app.mail.test=adres} ile başlatılınca açılışta
 * tek bir mail gönderir. Alan adı ayarlarını (SPF/DKIM/DMARC) denemek için;
 * dışarıya açık bir uç yok.
 */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
public class MailTestRunner implements CommandLineRunner {

    private final MailService mailService;

    @Value("${app.mail.test:}")
    private String testAddress;

    @Override
    public void run(String... args) {
        if (testAddress == null || testAddress.isBlank()) return;
        MailTemplates.Mail mail = MailTemplates.build(
                "Decouva deneme maili",
                "Merhaba!",
                List.of("Bu bir deneme mailidir. Bunu gelen kutunda görüyorsan Decouva'nın mail "
                        + "gönderimi çalışıyor demektir.",
                        "Spam klasörüne düştüyse bize haber ver."),
                null, null);
        try {
            mailService.send(testAddress.trim(), mail.subject(), mail.text(), mail.html());
        } catch (MailDeliveryException e) {
            log.error("Deneme maili gönderilemedi: {}", e.getMessage());
        }
    }
}
