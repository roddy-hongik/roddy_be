package com.roddy.domain.jobposting.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 회사마다 제각각인 날짜 표기를 읽는다.
 *
 * <p>같은 필드라도 {@code 2026.05.14 00:00:00}, {@code 2026-05-14}, ISO, epoch 밀리초가 모두 온다.
 * 읽지 못하는 값은 예외 대신 null 로 흘려보낸다. 날짜 하나 때문에 공고를 통째로 버릴 이유가 없다.
 */
public final class CrawledDateParser {

    private static final ZoneId SITE_ZONE = ZoneId.of("Asia/Seoul");

    /** 9999-12-31, 2999-12-31 처럼 "상시 채용"을 뜻하는 먼 미래 값은 마감일이 없는 것으로 본다. */
    private static final int PERPETUAL_YEAR = 2900;

    private static final Pattern EPOCH_MILLIS = Pattern.compile("\\d{13}");
    private static final Pattern EPOCH_SECONDS = Pattern.compile("\\d{10}");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    private CrawledDateParser() {
    }

    /** 읽지 못하거나 상시 채용을 뜻하는 값이면 null. */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        LocalDateTime parsed = parseEpoch(value);
        if (parsed == null) {
            parsed = parseDateTime(value);
        }
        if (parsed == null) {
            parsed = parseDate(value);
        }
        if (parsed == null) {
            parsed = parseOffset(value);
        }

        return parsed != null && parsed.getYear() >= PERPETUAL_YEAR ? null : parsed;
    }

    private static LocalDateTime parseEpoch(String value) {
        if (EPOCH_MILLIS.matcher(value).matches()) {
            return Instant.ofEpochMilli(Long.parseLong(value)).atZone(SITE_ZONE).toLocalDateTime();
        }
        if (EPOCH_SECONDS.matcher(value).matches()) {
            return Instant.ofEpochSecond(Long.parseLong(value)).atZone(SITE_ZONE).toLocalDateTime();
        }
        return null;
    }

    private static LocalDateTime parseDateTime(String value) {
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 넘어간다.
            }
        }
        return null;
    }

    private static LocalDateTime parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 넘어간다.
            }
        }
        return null;
    }

    /** 2026-05-01T00:00:00.000Z 처럼 시간대가 붙은 값. */
    private static LocalDateTime parseOffset(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(SITE_ZONE).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
