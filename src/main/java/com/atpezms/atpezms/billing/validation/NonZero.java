package com.atpezms.atpezms.billing.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a numeric value is not zero.
 * Used for amountCents where both positive (charges) and negative (credits)
 * are allowed, but zero is meaningless.
 */
@Documented
@Constraint(validatedBy = NonZeroValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonZero {
	String message() default "must not be zero";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
