package com.foodboxd.api.services.mail;

/** Mail gönderilemedi. Mesaj iç kullanım içindir; kullanıcıya genel bir metin gösterilir. */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
