package com.atpezms.atpezms.billing.exception;

import com.atpezms.atpezms.common.exception.ResourceNotFoundException;

public class BillNotFoundException extends ResourceNotFoundException {
	public BillNotFoundException(Long visitId) {
		super("BILL_NOT_FOUND", "No bill found for visit: id=" + visitId);
	}
}
