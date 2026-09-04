package com.foodboxd.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Şifre karmaşıklık kuralı: en az 8 karakter, bir büyük harf, bir küçük harf,
 * bir rakam ve bir özel karakter. İstemcide de doğrulanır ama asıl kontrol burada.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Şifre en az 8 karakter olmalı; bir büyük harf, "
            + "bir küçük harf, bir rakam ve bir özel karakter içermelidir";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
