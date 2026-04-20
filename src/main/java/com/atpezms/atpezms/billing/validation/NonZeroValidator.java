package com.atpezms.atpezms.billing.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NonZeroValidator implements ConstraintValidator<NonZero, Long> {

	@Override
	public boolean isValid(Long value, ConstraintValidatorContext context) {
		if (value == null) {
			return true; // let @NotNull handle null
		}
		return value != 0;
	}
}
