package com.foodboxd.api.services.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resend ile gönderim (2.6). Gönderen alan adı Resend'de doğrulanmış olmalı
 * (`decouva.com`, DKIM + `send.` alt alanı); yanıtlar destek adresine gider.
 */
@Slf4j
public class ResendMailService implements MailService {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient client;
    private final String from;
    private final String replyTo;

    public ResendMailService(String apiKey, String from, String replyTo) {
        this.client = RestClient.builder()
                .baseUrl(ENDPOINT)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
        this.replyTo = replyTo;
    }

    @Override
    public void send(String to, String subject, String text, String html) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("text", text);
        body.put("html", html);
        if (replyTo != null && !replyTo.isBlank()) body.put("reply_to", replyTo);
        try {
            client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Mail gönderildi: alıcı={}, konu=\"{}\"", MailService.mask(to), subject);
        } catch (RestClientException e) {
            log.error("Mail gönderilemedi: alıcı={}, konu=\"{}\", hata={}",
                    MailService.mask(to), subject, e.getMessage());
            throw new MailDeliveryException("Mail gönderilemedi.", e);
        }
    }
}
