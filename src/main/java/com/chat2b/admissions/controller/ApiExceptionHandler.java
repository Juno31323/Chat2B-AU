package com.chat2b.admissions.controller;

import com.chat2b.admissions.exception.AdminAccessDeniedException;
import com.chat2b.admissions.exception.AdminOperationThrottledException;
import com.chat2b.admissions.exception.RateLimitExceededException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(RateLimitExceededException.class)
	public ProblemDetail handleRateLimit(RateLimitExceededException exception) {
		return userProblem(
			HttpStatus.TOO_MANY_REQUESTS,
			"RATE_LIMITED",
			"\uC694\uCCAD\uC774 \uC7A0\uC2DC \uB9CE\uC544\uC694",
			"\uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.",
			"\uC870\uAE08 \uC788\uB2E4\uAC00 \uB2E4\uC2DC \uC9C8\uBB38\uD574 \uC8FC\uC138\uC694."
		);
	}

	@ExceptionHandler(AdminAccessDeniedException.class)
	public ProblemDetail handleAdminAccess(AdminAccessDeniedException exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		problemDetail.setTitle("Admin access denied");
		problemDetail.setDetail(exception.getMessage());
		return problemDetail;
	}

	@ExceptionHandler(AdminOperationThrottledException.class)
	public ProblemDetail handleAdminThrottle(AdminOperationThrottledException exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
		problemDetail.setTitle("Admin request blocked");
		problemDetail.setDetail(exception.getMessage());
		return problemDetail;
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
	public ProblemDetail handleBadRequest(Exception exception) {
		String validationMessage = extractBadRequestMessage(exception);
		if (validationMessage.contains("500\uC790")) {
			return userProblem(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST",
				"\uC9C8\uBB38\uC774 \uB108\uBB34 \uAE38\uC5B4\uC694",
				"\uC9C8\uBB38\uC740 500\uC790 \uC774\uD558\uB85C \uC785\uB825\uD574 \uC8FC\uC138\uC694.",
				"\uC9C8\uBB38\uC744 \uB098\uB220\uC11C \uB2E4\uC2DC \uBCF4\uB0B4 \uC8FC\uC138\uC694."
			);
		}
		if (validationMessage.contains("\uC9C8\uBB38\uC744 \uC785\uB825")) {
			return userProblem(
				HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST",
				"\uC9C8\uBB38\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694",
				"\uB0B4\uC6A9\uC744 \uC785\uB825\uD55C \uB4A4 \uB2E4\uC2DC \uBCF4\uB0B4 \uC8FC\uC138\uC694.",
				"\uC9E7\uAC8C \uD55C \uBB38\uC7A5\uC73C\uB85C \uC785\uB825\uD574\uB3C4 \uAD1C\uCC2E\uC544\uC694."
			);
		}
		return userProblem(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"\uC9C8\uBB38\uC744 \uB2E4\uC2DC \uD655\uC778\uD574 \uC8FC\uC138\uC694",
			"\uC785\uB825\uD55C \uB0B4\uC6A9\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694.",
			"\uC9C8\uBB38\uC744 \uC870\uAE08 \uB354 \uC9E7\uACE0 \uBD84\uBA85\uD558\uAC8C \uC801\uC5B4 \uC8FC\uC138\uC694."
		);
	}

	@ExceptionHandler(IllegalStateException.class)
	public ProblemDetail handleIllegalState(IllegalStateException exception) {
		log.warn("Public chat request failed due to application state.", exception);
		return userProblem(
			HttpStatus.SERVICE_UNAVAILABLE,
			"TEMPORARY_UNAVAILABLE",
			"\uC9C0\uAE08\uC740 \uB2F5\uBCC0\uC744 \uC900\uBE44\uD558\uC9C0 \uBABB\uD558\uACE0 \uC788\uC5B4\uC694",
			"\uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.",
			"\uBB38\uC81C\uAC00 \uACC4\uC18D\uB418\uBA74 \uC785\uD559\uCC98\uB85C \uBB38\uC758\uD574 \uC8FC\uC138\uC694."
		);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception exception) {
		log.error("Unexpected public chat error.", exception);
		return userProblem(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"UNEXPECTED_ERROR",
			"\uD604\uC7AC \uC624\uB958\uAC00 \uBC1C\uC0DD\uD588\uC5B4\uC694",
			"\uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.",
			"\uAC19\uC740 \uBB38\uC81C\uAC00 \uACC4\uC18D\uB418\uBA74 \uC785\uD559\uCC98\uB85C \uBB38\uC758\uD574 \uC8FC\uC138\uC694."
		);
	}

	private ProblemDetail userProblem(HttpStatus status, String code, String title, String detail, String action) {
		ProblemDetail problemDetail = ProblemDetail.forStatus(status);
		problemDetail.setTitle(title);
		problemDetail.setDetail(detail);
		problemDetail.setProperty("code", code);
		problemDetail.setProperty("action", action);
		return problemDetail;
	}

	private String extractBadRequestMessage(Exception exception) {
		if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
			return methodArgumentNotValidException.getBindingResult().getAllErrors().stream()
				.map(ObjectError::getDefaultMessage)
				.filter(message -> message != null && !message.isBlank())
				.findFirst()
				.orElse("\uC785\uB825\uD55C \uB0B4\uC6A9\uC744 \uB2E4\uC2DC \uD655\uC778\uD574 \uC8FC\uC138\uC694.");
		}
		if (exception instanceof ConstraintViolationException constraintViolationException) {
			return constraintViolationException.getConstraintViolations().stream()
				.map(ConstraintViolation::getMessage)
				.filter(message -> message != null && !message.isBlank())
				.findFirst()
				.orElse("\uC785\uB825\uD55C \uB0B4\uC6A9\uC744 \uB2E4\uC2DC \uD655\uC778\uD574 \uC8FC\uC138\uC694.");
		}
		String message = exception.getMessage();
		return message == null || message.isBlank()
			? "\uC785\uB825\uD55C \uB0B4\uC6A9\uC744 \uB2E4\uC2DC \uD655\uC778\uD574 \uC8FC\uC138\uC694."
			: message;
	}
}
