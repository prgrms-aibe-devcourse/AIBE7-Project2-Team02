package org.example.matcheat.domain.quote.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [주의] static/chat/js/chat-room.js의 encodeNotes/decodeNotes와 반드시 1:1 동일해야
 * 한다 — 한쪽만 고치면 즉시 데이터 파손. QuoteNegotiation에 전용 컬럼이 생기면
 * (estimate 필드 마이그레이션) 이 클래스는 통째로 삭제 대상이다.
 */
final class QuoteNegotiationNotesCodec {

	private static final String META_OPEN = "[MATCHEAT_META]";
	private static final String META_CLOSE = "[/MATCHEAT_META]";
	private static final String SUMMARY_OPEN = "[AI_SUMMARY]";
	private static final String SUMMARY_CLOSE = "[/AI_SUMMARY]";

	private static final Pattern META_PATTERN =
			Pattern.compile(Pattern.quote(META_OPEN) + "\\n([\\s\\S]*?)\\n" + Pattern.quote(META_CLOSE));
	private static final Pattern SUMMARY_PATTERN =
			Pattern.compile(Pattern.quote(SUMMARY_OPEN) + "\\n([\\s\\S]*?)\\n" + Pattern.quote(SUMMARY_CLOSE));

	private QuoteNegotiationNotesCodec() {
	}

	record Decoded(String eventDateTime, String budgetType, String budget,
	               String deliveryAddress, String description, String summary) {
		static Decoded empty() {
			return new Decoded(null, null, null, null, null, "");
		}
	}

	static Decoded decode(String raw) {
		if (raw == null || raw.isBlank()) return Decoded.empty();

		Matcher metaMatcher = META_PATTERN.matcher(raw);
		Matcher summaryMatcher = SUMMARY_PATTERN.matcher(raw);
		boolean hasMeta = metaMatcher.find();
		boolean hasSummary = summaryMatcher.find();

		if (!hasMeta && !hasSummary) {
			return new Decoded(null, null, null, null, null, raw); // 이전 포맷 호환
		}

		Map<String, String> fields = new LinkedHashMap<>();
		if (hasMeta) {
			for (String line : metaMatcher.group(1).split("\n")) {
				int idx = line.indexOf('=');
				if (idx < 0) continue;
				fields.put(line.substring(0, idx), line.substring(idx + 1).replace("\\n", "\n"));
			}
		}
		String summary = hasSummary ? summaryMatcher.group(1) : "";

		return new Decoded(
				fields.get("eventDateTime"), fields.get("budgetType"), fields.get("budget"),
				fields.get("deliveryAddress"), fields.get("description"), summary);
	}

	static String encode(Decoded fields) {
		StringBuilder sb = new StringBuilder();
		sb.append(META_OPEN).append('\n');
		appendLine(sb, "eventDateTime", fields.eventDateTime());
		appendLine(sb, "budgetType", fields.budgetType());
		appendLine(sb, "budget", fields.budget());
		appendLine(sb, "deliveryAddress", fields.deliveryAddress());
		appendLine(sb, "description", fields.description());
		sb.append(META_CLOSE).append('\n');
		sb.append(SUMMARY_OPEN).append('\n');
		sb.append(fields.summary() != null ? fields.summary() : "");
		sb.append('\n').append(SUMMARY_CLOSE);
		return sb.toString();
	}

	private static void appendLine(StringBuilder sb, String key, String value) {
		if (value == null || value.isBlank()) return;
		sb.append(key).append('=').append(value.replace("\n", "\\n")).append('\n');
	}
}