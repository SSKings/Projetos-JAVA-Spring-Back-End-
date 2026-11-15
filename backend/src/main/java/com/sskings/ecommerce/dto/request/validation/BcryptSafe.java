package com.sskings.ecommerce.dto.request.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Senha com no máximo 72 <b>bytes</b> em UTF-8. O BCrypt ignora tudo depois do 72º byte; @Size conta caracteres,
 * e "ç", "ã" ou emoji ocupam 2 a 4 bytes — uma senha de 72 caracteres acentuados era cortada em silêncio
 * e qualquer variação no final dela também entrava (revisão 3, B2).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BcryptSafe.Validator.class)
public @interface BcryptSafe {

    int MAX_BYTES = 72;

    String message() default "Password is too long (accented characters count as 2 or more)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BcryptSafe, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
