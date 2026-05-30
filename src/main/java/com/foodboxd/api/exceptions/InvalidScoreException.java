package com.foodboxd.api.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidScoreException extends RuntimeException {

    public InvalidScoreException(String mesaj) {
        super(mesaj);
    }
}
