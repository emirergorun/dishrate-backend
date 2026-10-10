package com.foodboxd.api.exceptions;

/** 429: istek ya da deneme sınırı aşıldı (2.9). Mesaj kullanıcıya gider. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
