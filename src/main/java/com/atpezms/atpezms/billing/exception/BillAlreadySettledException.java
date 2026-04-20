package com.atpezms.atpezms.billing.exception;

import com.atpezms.atpezms.common.exception.BusinessRuleViolationException;

public class BillAlreadySettledException extends BusinessRuleViolationException {
	public BillAlreadySettledException(Long visitId) {
		super("BILL_ALREADY_SETTLED", "Bill for visit is already settled: id=" + visitId);
	}
}
