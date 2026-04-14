package com.atpezms.atpezms.common.exception;

import com.atpezms.atpezms.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized error mapping so controllers can stay "thin":
 * - Validate input at the boundary (Bean Validation)
 * - Delegate to a service
 * - Let exceptions bubble up
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
		var status = ex.getHttpStatus();
		return ResponseEntity.status(status)
				.body(new ErrorResponse(
						status.value(),
						ex.getErrorCode(),
						ex.getMessage(),
						Instant.now(),
						List.of()
				));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldError)
				.toList();

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(
						HttpStatus.BAD_REQUEST.value(),
						"VALIDATION_FAILED",
						"Request validation failed",
						Instant.now(),
						fieldErrors
				));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		// Method-parameter validation (e.g. @PathVariable/@RequestParam) raises ConstraintViolationException.
		// We map it to the same 400 structure as body validation so clients don't need two parsers.
		var fieldErrors = ex.getConstraintViolations().stream()
				.map(v -> {
					String field = v.getPropertyPath() != null
							? v.getPropertyPath().toString()
							: "parameter";
					// Typical path example: "getActiveVisitByRfidTag.rfidTag". We only expose the parameter name.
					int idx = field.lastIndexOf('.');
					if (idx >= 0 && idx < field.length() - 1) {
						field = field.substring(idx + 1);
					}
					return new ErrorResponse.FieldError(field, v.getMessage());
				})
				.toList();

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(
						HttpStatus.BAD_REQUEST.value(),
						"VALIDATION_FAILED",
						"Request validation failed",
						Instant.now(),
						fieldErrors
				));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(
						HttpStatus.BAD_REQUEST.value(),
						"MALFORMED_JSON",
						"Malformed request body",
						Instant.now(),
						List.of()
				));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(new ErrorResponse(
						HttpStatus.METHOD_NOT_ALLOWED.value(),
						"METHOD_NOT_ALLOWED",
						"HTTP method not allowed for this endpoint",
						Instant.now(),
						List.of()
				));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		// Log the full stack trace so unexpected errors are traceable in server logs.
		// We deliberately do NOT include ex.getMessage() in the response body to avoid
		// leaking internal implementation details to API clients.
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse(
						HttpStatus.INTERNAL_SERVER_ERROR.value(),
						"INTERNAL_ERROR",
						"Unexpected server error",
						Instant.now(),
						List.of()
				));
	}

	private ErrorResponse.FieldError toFieldError(FieldError err) {
		return new ErrorResponse.FieldError(err.getField(), err.getDefaultMessage());
	}
}
