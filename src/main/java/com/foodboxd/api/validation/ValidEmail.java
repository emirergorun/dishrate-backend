package com.foodboxd.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * E-posta biçim kuralı.
 *
 * Jakarta'nın {@code @Email} kuralı kasıtlı olarak gevşektir — RFC 5321 teknik
 * olarak alan adı kısmında nokta zorunlu tutmaz, bu yüzden {@code ali@user} ya
 * da {@code ali@localhost} geçerli sayılır. İnternet üzerinden e-posta alacak
 * bir uygulama için bu yeterli değil: kullanıcı {@code .com} yazmayı unuttuysa
 * adres asla teslim edilemez ve bunu ancak posta geri döndüğünde anlarız.
 *
 * Bu kural alan adında en az bir nokta ve en az iki harflik bir uzantı arar.
 *
 * Not: hiçbir biçim kuralı adresin gerçekten var olduğunu kanıtlamaz. Onun için
 * doğrulama postası göndermek gerekir — bkz. şifre sıfırlama çalışması.
 */
@Documented
@Constraint(validatedBy = ValidEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

    String message() default "Geçerli bir e-posta adresi giriniz (örn. ad@ornek.com)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
