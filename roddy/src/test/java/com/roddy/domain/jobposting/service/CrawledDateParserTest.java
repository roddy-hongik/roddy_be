package com.roddy.domain.jobposting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CrawledDateParserTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "2026-05-14T09:30:00, 2026-05-14T09:30:00",
            "2026-05-14 09:30:00, 2026-05-14T09:30:00",
            "2026.05.14 09:30:00, 2026-05-14T09:30:00",
            "2026/05/14 09:30:00, 2026-05-14T09:30:00",
            "2026-05-14 09:30,    2026-05-14T09:30:00",
            "2026-05-14,          2026-05-14T00:00:00",
            "2026.05.14,          2026-05-14T00:00:00",
            "20260514,            2026-05-14T00:00:00"
    })
    @DisplayName("회사마다 다른 날짜 표기를 읽는다")
    void parsesVariousFormats(String raw, String expected) {
        assertThat(CrawledDateParser.parse(raw)).isEqualTo(LocalDateTime.parse(expected));
    }

    @Test
    @DisplayName("시간대가 붙은 값은 한국 시각으로 바꿔 읽는다")
    void parsesOffsetDateTime() {
        assertThat(CrawledDateParser.parse("2026-05-14T00:30:00Z"))
                .isEqualTo(LocalDateTime.of(2026, 5, 14, 9, 30));
    }

    @Test
    @DisplayName("epoch 밀리초와 초를 읽는다")
    void parsesEpoch() {
        // 2026-05-14T09:30:00+09:00
        assertThat(CrawledDateParser.parse("1778718600000"))
                .isEqualTo(CrawledDateParser.parse("1778718600"));
        assertThat(CrawledDateParser.parse("1778718600000")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999-12-31", "2999-12-31 23:59:59", "2999.12.31"})
    @DisplayName("상시 채용을 뜻하는 먼 미래 값은 마감일이 없는 것으로 본다")
    void treatsPerpetualDateAsNull(String raw) {
        assertThat(CrawledDateParser.parse(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"상시채용", "채용시 마감", "-", "TBD"})
    @DisplayName("읽지 못하는 값은 예외 대신 null 을 돌려준다")
    void returnsNullForUnparseable(String raw) {
        assertThat(CrawledDateParser.parse(raw)).isNull();
    }

    @Test
    @DisplayName("값이 없으면 null 이다")
    void returnsNullForBlank() {
        assertThat(CrawledDateParser.parse(null)).isNull();
        assertThat(CrawledDateParser.parse("  ")).isNull();
    }
}
