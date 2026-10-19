package com.vikashini.taskmaster.auth;

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
 * A new password: at least 8 characters and at most 72 bytes once encoded as UTF-8.
 * BCrypt only uses the first 72 bytes and Spring refuses longer input outright, so the
 * upper limit is checked in bytes rather than characters.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {

    String message() default "must be between 8 characters and 72 bytes long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {

        static final int MIN_CHARACTERS = 8;
        static final int MAX_BYTES = 72;

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value != null
                    && value.length() >= MIN_CHARACTERS
                    && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
