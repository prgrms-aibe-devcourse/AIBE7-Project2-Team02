package org.example.matcheat.domain.quote.ai;

import lombok.extern.slf4j.Slf4j;
import org.example.matcheat.domain.quote.entity.QuoteNegotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Slf4j
final class AiQuoteSummaryGuard {

	private static final int MAX_QUANTITY = 100_000;
	private static final BigDecimal MAX_BUDGET = BigDecimal.valueOf(1_000_000_000L);
	private static final int MAX_TEXT_LENGTH = 500;
	private static final int SUSPICIOUS_MULTIPLIER = 20;

	private AiQuoteSummaryGuard() {
	}

	static AiNegotiationFieldsResult sanitize(
			AiNegotiationFieldsResult raw, QuoteNegotiation negotiation, QuoteNegotiationNotesCodec.Decoded current) {

		AiNegotiationFieldsResult safe = new AiNegotiationFieldsResult();
		safe.setQuantity(sanitizeQuantity(raw.getQuantity(), negotiation.getQuantity()));
		safe.setBudgetType(sanitizeBudgetType(raw.getBudgetType()));
		safe.setBudget(sanitizeBudget(raw.getBudget(), current.budget()));
		safe.setEventDateTime(sanitizeEventDateTime(raw.getEventDateTime()));
		safe.setDeliveryAddress(sanitizeText(raw.getDeliveryAddress(), 255));
		safe.setDescription(sanitizeText(raw.getDescription(), MAX_TEXT_LENGTH));
		safe.setSummaryNote(sanitizeText(raw.getSummaryNote(), MAX_TEXT_LENGTH));
		return safe;
	}

	private static Integer sanitizeQuantity(Integer value, Integer current) {
		if (value == null) return null;
		if (value <= 0 || value > MAX_QUANTITY) {
			log.warn("AI 요약 수량이 상식 범위를 벗어나 무시함: {}", value);
			return null;
		}
		if (isSuspiciousJump(value, current)) {
			log.warn("AI 요약 수량이 기존 값 대비 급변동해 무시함: {} -> {}", current, value);
			return null;
		}
		return value;
	}

	private static String sanitizeBudgetType(String value) {
		if (value == null) return null;
		if (!value.equals("PER_PERSON") && !value.equals("TOTAL")) {
			log.warn("AI 요약 budgetType이 허용 범위 밖이라 무시함: {}", value);
			return null;
		}
		return value;
	}

	private static BigDecimal sanitizeBudget(BigDecimal value, String currentStr) {
		if (value == null) return null;
		if (value.signum() < 0 || value.compareTo(MAX_BUDGET) > 0) {
			log.warn("AI 요약 예산이 상식 범위를 벗어나 무시함: {}", value);
			return null;
		}
		try {
			if (currentStr != null) {
				BigDecimal current = new BigDecimal(currentStr);
				if (current.signum() != 0) {
					BigDecimal ratio = value.divide(current, 4, RoundingMode.HALF_UP);
					BigDecimal minRatio = BigDecimal.ONE.divide(BigDecimal.valueOf(SUSPICIOUS_MULTIPLIER), 4, RoundingMode.HALF_UP);
					if (ratio.compareTo(BigDecimal.valueOf(SUSPICIOUS_MULTIPLIER)) > 0 || ratio.compareTo(minRatio) < 0) {
						log.warn("AI 요약 예산이 기존 값 대비 급변동해 무시함: {} -> {}", currentStr, value);
						return null;
					}
				}
			}
		} catch (NumberFormatException ignored) {
			// 기존 값이 아직 없던 최초 협상인 경우 — 비교를 건너뛴다.
		}
		return value;
	}

	private static String sanitizeEventDateTime(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			LocalDateTime.parse(value);
			return value;
		} catch (DateTimeParseException e) {
			log.warn("AI 요약 eventDateTime 형식이 올바르지 않아 무시함: {}", value);
			return null;
		}
	}

	private static String sanitizeText(String value, int maxLength) {
		if (value == null) return null;
		String trimmed = value.strip();
		if (trimmed.isEmpty()) return null;
		if (trimmed.length() > maxLength) {
			log.warn("AI 요약 텍스트가 길이 제한({}) 초과해 잘라냄: {}자", maxLength, trimmed.length());
			trimmed = trimmed.substring(0, maxLength);
		}
		return trimmed;
	}

	private static boolean isSuspiciousJump(Number newValue, Number currentValue) {
		if (currentValue == null || currentValue.doubleValue() == 0) return false;
		double ratio = newValue.doubleValue() / currentValue.doubleValue();
		return ratio > SUSPICIOUS_MULTIPLIER || ratio < (1.0 / SUSPICIOUS_MULTIPLIER);
	}
}