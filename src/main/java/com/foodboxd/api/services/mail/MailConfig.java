package com.foodboxd.api.services.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hangi gönderici: Resend anahtarı varsa gerçek gönderim, yoksa log (2.6).
 * Anahtar boş metin olarak da gelebiliyor (yer tutucunun varsayılanı), o
 * yüzden {@code @ConditionalOnProperty} yerine burada karar veriliyor.
 */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public MailService mailService(@Value("${app.mail.resend-api-key:}") String apiKey,
                                   @Value("${app.mail.from}") String from,
                                   @Value("${app.mail.reply-to:}") String replyTo) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend anahtarı yok: mailler gönderilmeyecek, yalnız log'a yazılacak.");
            return new LogMailService();
        }
        log.info("Mail gönderimi: Resend ({}).", from);
        return new ResendMailService(apiKey.trim(), from, replyTo);
    }
}
