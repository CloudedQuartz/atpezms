package com.atpezms.atpezms.telemetry.dto;

import com.atpezms.atpezms.telemetry.entity.ScanDecision;
import com.atpezms.atpezms.telemetry.entity.ScanPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecordScanRequest(
	@NotBlank @Size(max = 100) String rfidTag,
	@NotBlank @Size(max = 100) String deviceIdentifier,
	@NotNull @Positive Long zoneId,
	@NotNull ScanPurpose purpose,
	@NotNull ScanDecision decision,
	@Size(max = 500) String reason
) {}
