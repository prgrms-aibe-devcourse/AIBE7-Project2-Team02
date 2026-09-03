package org.example.matcheat.domain.quote.exception;

public class AiSummaryFailedException extends RuntimeException {
	public AiSummaryFailedException(String message) {
		super(message);
	}

	public AiSummaryFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}