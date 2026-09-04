package com.foodboxd.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator
        implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;

    // Türkçe harfler de büyük/küçük olarak sayılır
    private static final Pattern UPPER = Pattern.compile("[A-ZÇĞİÖŞÜ]");
    private static final Pattern LOWER = Pattern.compile("[a-zçğıöşü]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL =
            Pattern.compile("[^A-Za-zÇĞİÖŞÜçğıöşü0-9]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null kontrolü @NotBlank'in işi; burada sadece karmaşıklığa bakılır
        if (value == null) return true;

        return value.length() >= MIN_LENGTH
                && UPPER.matcher(value).find()
                && LOWER.matcher(value).find()
                && DIGIT.matcher(value).find()
                && SPECIAL.matcher(value).find();
    }
}
