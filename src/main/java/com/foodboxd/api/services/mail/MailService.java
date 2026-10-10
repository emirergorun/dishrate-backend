package com.foodboxd.api.services.mail;

/**
 * E-posta gönderimi (2.6). Uygulaması {@link MailConfig}'te seçilir:
 * Resend anahtarı varsa {@link ResendMailService}, yoksa
 * {@link LogMailService} (geliştirme — mail gitmez, log'a yazılır).
 *
 * <p>Alıcı adresi log'a tam yazılmaz ({@link #mask}); içerik hiç yazılmaz
 * (şifre sıfırlama bağlantısı gibi sır taşıyabilir).
 */
public interface MailService {

    /**
     * @throws MailDeliveryException gönderilemezse
     */
    void send(String to, String subject, String text, String html);

    /** "emir@gmail.com" → "e***@gmail.com" */
    static String mask(String address) {
        if (address == null) return "-";
        int at = address.indexOf('@');
        if (at <= 0) return "***";
        return address.charAt(0) + "***" + address.substring(at);
    }
}
