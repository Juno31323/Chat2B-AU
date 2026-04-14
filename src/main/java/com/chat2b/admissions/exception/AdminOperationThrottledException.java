package com.chat2b.admissions.exception;

public class AdminOperationThrottledException extends RuntimeException {

	public AdminOperationThrottledException(String message) {
		super(message);
	}
}
